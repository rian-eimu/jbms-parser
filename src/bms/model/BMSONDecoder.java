package bms.model;

import java.io.*;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static bms.model.DecodeLog.State.*;

import bms.model.Layer.EventType;
import bms.model.bmson.*;
import bms.model.bmson.Note;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * bmsonデコーダー
 * 
 * @author exch
 */
public class BMSONDecoder extends ChartDecoder {

	private final ObjectMapper mapper = new ObjectMapper();

	private BMSModel model;

	private final TreeMap<Integer, TimeLineCache> tlcache = new TreeMap<Integer, TimeLineCache>();

	private static final Comparator<BMSONObject> COMPARATOR = (n1, n2) -> Integer.compare(n1.y, n2.y);

	public BMSONDecoder(int lntype) {
		this.lntype = lntype;
	}

	public BMSModel decode(ChartInformation info) {
		this.lntype = info.lntype;
		return decode(info, info.source != null ? info.source : (info.path != null ? new PathChartSource(info.path) : null));
	}

	public BMSModel decode(Path f) {
		return decode(new ChartInformation(f, lntype, null));
	}

	/**
	 * チャネルに割り当てられたWAV IDとチャネル定義のペア
	 */
	private static class AssignedSoundChannel {
		final int wavId;
		final SoundChannel channel;

		AssignedSoundChannel(int wavId, SoundChannel channel) {
			this.wavId = wavId;
			this.channel = channel;
		}
	}

	private static class AssignedMineChannel {
		final int wavId;
		final MineChannel channel;

		AssignedMineChannel(int wavId, MineChannel channel) {
			this.wavId = wavId;
			this.channel = channel;
		}
	}

	private static class AssignedFreeZoneChannel {
		final int wavId;
		final FreeZoneChannel channel;

		AssignedFreeZoneChannel(int wavId, FreeZoneChannel channel) {
			this.wavId = wavId;
			this.channel = channel;
		}
	}

	/**
	 * サニタイズおよびWAV ID割り当て済みの分岐データ
	 */
	private static class PreparedBranch {
		final bms.model.ConditionalBlock.Branch modelBranch;
		final List<BarLine> lines = new ArrayList<>();
		final List<BpmEvent> bpmEvents = new ArrayList<>();
		final List<StopEvent> stopEvents = new ArrayList<>();
		final List<ScrollEvent> scrollEvents = new ArrayList<>();
		final List<ScrollEvent> speedEvents = new ArrayList<>();
		final List<AssignedSoundChannel> soundChannels = new ArrayList<>();
		final List<AssignedMineChannel> keyChannels = new ArrayList<>();
		final List<AssignedMineChannel> mineChannels = new ArrayList<>();
		final List<AssignedFreeZoneChannel> freeZoneChannels = new ArrayList<>();
		final List<bms.model.bmson.PotionNote> potionNotes = new ArrayList<>();
		final List<Note> dummyNotes = new ArrayList<>();
		final List<bms.model.bmson.MineNote> dummyMineNotes = new ArrayList<>();
		final List<bms.model.bmson.MineNote> dummyPotionNotes = new ArrayList<>();
		final List<BNote> bgaEvents = new ArrayList<>();
		final List<BNote> layerEvents = new ArrayList<>();
		final List<BNote> poorEvents = new ArrayList<>();

		PreparedBranch(bms.model.ConditionalBlock.Branch modelBranch) {
			this.modelBranch = modelBranch;
		}
	}

	/**
	 * パース・検証済みの動的条件分岐ブロック
	 */
	private static class PreparedConditionalBlock {
		final int evalY;
		final bms.model.ConditionalBlock modelBlock;
		final List<PreparedBranch> branches;

		PreparedConditionalBlock(int evalY, bms.model.ConditionalBlock modelBlock, List<PreparedBranch> branches) {
			this.evalY = evalY;
			this.modelBlock = modelBlock;
			this.branches = branches;
		}
	}

	/**
	 * BMSON譜面再構築用のコンテキスト
	 */
	private static class BmsonBuildContext {
		final double resolution;
		final int quarterPulse;
		final int[] keyassign;
		final List<BarLine> rootLines = new ArrayList<>();
		final List<BpmEvent> rootBpmEvents = new ArrayList<>();
		final List<StopEvent> rootStopEvents = new ArrayList<>();
		final List<ScrollEvent> rootScrollEvents = new ArrayList<>();
		final List<ScrollEvent> rootSpeedEvents = new ArrayList<>();
		final List<AssignedSoundChannel> rootSoundChannels = new ArrayList<>();
		final List<AssignedMineChannel> rootKeyChannels = new ArrayList<>();
		final List<AssignedMineChannel> rootMineChannels = new ArrayList<>();
		final List<AssignedFreeZoneChannel> rootFreeZoneChannels = new ArrayList<>();
		final List<bms.model.bmson.PotionNote> rootPotionNotes = new ArrayList<>();
		final List<Note> rootDummyNotes = new ArrayList<>();
		final List<bms.model.bmson.MineNote> rootDummyMineNotes = new ArrayList<>();
		final List<bms.model.bmson.MineNote> rootDummyPotionNotes = new ArrayList<>();
		final List<BNote> rootBgaEvents = new ArrayList<>();
		final List<BNote> rootLayerEvents = new ArrayList<>();
		final List<BNote> rootPoorEvents = new ArrayList<>();
		final Map<Integer, Integer> bgaIdMap = new HashMap<>();
		final Map<Integer, Layer.Sequence[]> bgaSeqMap = new HashMap<>();
		final List<PreparedConditionalBlock> conditionalBlocks = new ArrayList<>();

		BmsonBuildContext(double resolution, int quarterPulse, int[] keyassign) {
			this.resolution = resolution;
			this.quarterPulse = quarterPulse;
			this.keyassign = keyassign;
		}
	}

	private BMSModel decode(ChartInformation info, ChartSource source) {
		if (source == null) {
			return null;
		}
		final String f = source.location();
		Logger.getGlobal().fine("BMSONファイル解析開始 :" + f.toString());
		log.clear();
		tlcache.clear();
		final long currnttime = System.currentTimeMillis();
		// BMS読み込み、ハッシュ値取得
		model = new BMSModel();
		Bmson bmson = null;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream is = source.openStream()) {
				bmson = mapper.readValue(new DigestInputStream(new BufferedInputStream(is), digest), Bmson.class);
			}

			model.setSHA256(BMSDecoder.convertHexString(digest.digest()));
		} catch (NoSuchAlgorithmException | IOException e) {
			e.printStackTrace();
			return null;
		}
		if (!validate(bmson)) {
			printLog(f);
			return null;
		}

		model.setTitle(bmson.info.title);
		model.setSubTitle((bmson.info.subtitle != null ? bmson.info.subtitle : "")
				+ (bmson.info.subtitle != null && bmson.info.subtitle.length() > 0 && bmson.info.chart_name != null
						&& bmson.info.chart_name.length() > 0 ? " " : "")
				+ (bmson.info.chart_name != null && bmson.info.chart_name.length() > 0
						? "[" + bmson.info.chart_name + "]" : ""));
		model.setArtist(bmson.info.artist);
		StringBuilder subartist = new StringBuilder();
		if (bmson.info.subartists != null) {
			for (String s : bmson.info.subartists) {
				subartist.append((subartist.length() > 0 ? "," : "") + s);
			}
		}
		model.setSubArtist(subartist.toString());
		model.setGenre(bmson.info.genre);

		if (bmson.info.judge_rank < 0) {
			log.add(new DecodeLog(WARNING, "judge_rankが0以下です。judge_rank = " + bmson.info.judge_rank));
		} else if (bmson.info.judge_rank < 5) {
			model.setJudgerank(bmson.info.judge_rank);
			log.add(new DecodeLog(WARNING, "judge_rankの定義が仕様通りでない可能性があります。judge_rank = " + bmson.info.judge_rank));
			model.setJudgerankType(BMSModel.JudgeRankType.BMS_RANK);
		} else {
			model.setJudgerank(bmson.info.judge_rank);
			model.setJudgerankType(BMSModel.JudgeRankType.BMSON_JUDGERANK);
		}

		if (bmson.info.total > 0) {
			model.setTotal(bmson.info.total);
			model.setTotalType(BMSModel.TotalType.BMSON);
		} else {
			log.add(new DecodeLog(WARNING, "totalが0以下です。total = " + bmson.info.total));
		}

		model.setBpm(bmson.info.init_bpm);
		model.setPlaylevel(String.valueOf(bmson.info.level));
		final Mode mode = Mode.getMode(bmson.info.mode_hint);
		if (mode != null) {
			model.setMode(mode);
		} else {
			log.add(new DecodeLog(WARNING, "非対応のmode_hintです。mode_hint = " + bmson.info.mode_hint));
			model.setMode(Mode.BEAT_7K);
		}
		if (bmson.info.ln_type > 0 && bmson.info.ln_type <= 4) {
			model.setLnmode(bmson.info.ln_type);
		}
		final int[] keyassign;
		switch (model.getMode()) {
		case BEAT_5K:
			keyassign = new int[] { 0, 1, 2, 3, 4, -1, -1, 5 };
			break;
		case BEAT_10K:
			keyassign = new int[] { 0, 1, 2, 3, 4, -1, -1, 5, 6, 7, 8, 9, 10, -1, -1, 11 };
			break;
		default:
			keyassign = new int[model.getMode().key];
			for (int i = 0; i < keyassign.length; i++) {
				keyassign[i] = i;
			}
		}

		model.setBanner(bmson.info.banner_image);
		model.setBackbmp(bmson.info.back_image);
		model.setStagefile(bmson.info.eyecatch_image);
		model.setPreview(bmson.info.preview_music);

		final int quarterPulse = bmson.info.resolution > 0 ? bmson.info.resolution : 240;
		final double resolution = quarterPulse * 4.0;
		final BmsonBuildContext ctx = new BmsonBuildContext(resolution, quarterPulse, keyassign);

		final List<String> wavList = new ArrayList<>();

		// ルート要素の収集とWAV ID割り当て
		if (bmson.lines != null) {
			for (BarLine bl : bmson.lines) {
				if (bl != null) {
					ctx.rootLines.add(bl);
				}
			}
		}
		if (bmson.bpm_events != null) {
			for (BpmEvent ev : bmson.bpm_events) {
				if (ev != null) {
					ctx.rootBpmEvents.add(ev);
				}
			}
		}
		if (bmson.stop_events != null) {
			for (StopEvent ev : bmson.stop_events) {
				if (ev != null) {
					ctx.rootStopEvents.add(ev);
				}
			}
		}
		if (bmson.scroll_events != null) {
			for (ScrollEvent ev : bmson.scroll_events) {
				if (ev != null) {
					ctx.rootScrollEvents.add(ev);
				}
			}
		}
		if (bmson.speed_events != null) {
			for (ScrollEvent ev : bmson.speed_events) {
				if (ev != null) {
					ctx.rootSpeedEvents.add(ev);
				}
			}
		}
		if (bmson.sound_channels != null) {
			for (SoundChannel sc : bmson.sound_channels) {
				if (sc != null) {
					final int id = wavList.size();
					wavList.add(sc.name);
					ctx.rootSoundChannels.add(new AssignedSoundChannel(id, copySortedSoundChannel(sc, Integer.MIN_VALUE, null)));
				}
			}
		}
		if (bmson.key_channels != null) {
			for (MineChannel mc : bmson.key_channels) {
				if (mc != null) {
					final int id = wavList.size();
					wavList.add(mc.name);
					ctx.rootKeyChannels.add(new AssignedMineChannel(id, copySortedMineChannel(mc, Integer.MIN_VALUE, null)));
				}
			}
		}
		if (bmson.mine_channels != null) {
			for (MineChannel mc : bmson.mine_channels) {
				if (mc != null) {
					final int id = wavList.size();
					wavList.add(mc.name);
					ctx.rootMineChannels.add(new AssignedMineChannel(id, copySortedMineChannel(mc, Integer.MIN_VALUE, null)));
				}
			}
		}
		if (bmson.free_zone_channels != null) {
			for (FreeZoneChannel fzc : bmson.free_zone_channels) {
				if (fzc != null) {
					final int id;
					if (fzc.name != null && !fzc.name.isEmpty()) {
						id = wavList.size();
						wavList.add(fzc.name);
					} else {
						id = -1;
					}
					ctx.rootFreeZoneChannels.add(new AssignedFreeZoneChannel(id, copySortedFreeZoneChannel(fzc, Integer.MIN_VALUE, null)));
				}
			}
		}
		if (bmson.potion_notes != null) {
			for (bms.model.bmson.PotionNote pn : bmson.potion_notes) {
				if (pn != null) {
					ctx.rootPotionNotes.add(pn);
				}
			}
		}
		if (bmson.dummy_notes != null) {
			for (Note dn : bmson.dummy_notes) {
				if (dn != null) {
					ctx.rootDummyNotes.add(dn);
				}
			}
		}
		if (bmson.dummy_mine_notes != null) {
			for (bms.model.bmson.MineNote dmn : bmson.dummy_mine_notes) {
				if (dmn != null) {
					ctx.rootDummyMineNotes.add(dmn);
				}
			}
		}
		if (bmson.dummy_potion_notes != null) {
			for (bms.model.bmson.MineNote dpn : bmson.dummy_potion_notes) {
				if (dpn != null) {
					ctx.rootDummyPotionNotes.add(dpn);
				}
			}
		}

		// BGAヘッダ・シーケンス・ルートイベントの収集
		if (bmson.bga != null) {
			if (bmson.bga.bga_header != null) {
				final String[] bgamap = new String[bmson.bga.bga_header.length];
				for (int i = 0; i < bmson.bga.bga_header.length; i++) {
					BGAHeader bh = bmson.bga.bga_header[i];
					bgamap[i] = bh.name;
					ctx.bgaIdMap.put(bh.id, i);
				}
				model.setBgaList(bgamap);
			}
			if (bmson.bga.bga_sequence != null) {
				for (BGASequence n : bmson.bga.bga_sequence) {
					if (n != null && n.sequence != null) {
						Layer.Sequence[] sequence = new Layer.Sequence[n.sequence.length];
						for (int i = 0; i < sequence.length; i++) {
							Sequence seq = n.sequence[i];
							if (seq.id != Integer.MIN_VALUE) {
								sequence[i] = new Layer.Sequence(seq.time, seq.id);
							} else {
								sequence[i] = new Layer.Sequence(seq.time);
							}
						}
						ctx.bgaSeqMap.put(n.id, sequence);
					}
				}
			}
			if (bmson.bga.bga_events != null) {
				for (BNote n : bmson.bga.bga_events) {
					if (n != null) {
						ctx.rootBgaEvents.add(n);
					}
				}
			}
			if (bmson.bga.layer_events != null) {
				for (BNote n : bmson.bga.layer_events) {
					if (n != null) {
						ctx.rootLayerEvents.add(n);
					}
				}
			}
			if (bmson.bga.poor_events != null) {
				for (BNote n : bmson.bga.poor_events) {
					if (n != null) {
						ctx.rootPoorEvents.add(n);
					}
				}
			}
		}

		// conditional_blocks のパース・検証・サニタイズ・WAV割り当て
		final Set<Integer> condWavIdSet = new TreeSet<>();
		boolean condHasMine = false;
		boolean condHasFreeZone = false;

		if (bmson.conditional_blocks != null) {
			final List<bms.model.bmson.ConditionalBlock> sortedRawBlocks = new ArrayList<>();
			for (bms.model.bmson.ConditionalBlock rb : bmson.conditional_blocks) {
				if (rb != null) {
					sortedRawBlocks.add(rb);
				}
			}
			sortedRawBlocks.sort((a, b) -> Integer.compare(a.y, b.y));
			for (bms.model.bmson.ConditionalBlock rawBlock : sortedRawBlocks) {
				if (rawBlock == null) {
					continue;
				}
				if (rawBlock.y < 0) {
					log.add(new DecodeLog(ERROR, "conditional_blocksの評価位置yが負の値です : " + rawBlock.y));
					continue;
				}
				if (rawBlock.branches == null || rawBlock.branches.length == 0) {
					log.add(new DecodeLog(ERROR, "conditional_blocksにbranchesが定義されていません : y=" + rawBlock.y));
					continue;
				}

				boolean blockHasDefault = false;
				for (ConditionalBranch b : rawBlock.branches) {
					if (b != null && b.defaultBranch) {
						blockHasDefault = true;
						break;
					}
				}
				if (!blockHasDefault) {
					log.add(new DecodeLog(ERROR, "conditional_blocksにdefault分岐が存在しません : y=" + rawBlock.y));
					continue;
				}

				final int evalY = rawBlock.y;
				final List<PreparedBranch> preparedBranches = new ArrayList<>();
				final List<bms.model.ConditionalBlock.Branch> modelBranches = new ArrayList<>();
				boolean hasDefault = false;

				for (ConditionalBranch rawBranch : rawBlock.branches) {
					if (rawBranch == null) {
						continue;
					}
					final bms.model.ConditionalBlock.Branch mb;
					if (rawBranch.defaultBranch) {
						if (hasDefault) {
							log.add(new DecodeLog(ERROR, "conditional_blocksに複数のdefault分岐が存在します : y=" + evalY));
							continue;
						}
						hasDefault = true;
						mb = new bms.model.ConditionalBlock.Branch(null);
					} else if (rawBranch.when != null) {
						final bms.model.ConditionalBlock.Condition cond = bms.model.ConditionalBlock.Condition.parse(rawBranch.when);
						if (cond == null) {
							log.add(new DecodeLog(ERROR, "conditional_blocksのwhen条件式が不正です : " + rawBranch.when));
							continue;
						}
						mb = new bms.model.ConditionalBlock.Branch(cond);
					} else {
						log.add(new DecodeLog(ERROR, "conditional_blocksの分岐にwhenもdefaultも定義されていません : y=" + evalY));
						continue;
					}

					final PreparedBranch pb = new PreparedBranch(mb);
					if (rawBranch.lines != null) {
						for (BarLine bl : rawBranch.lines) {
							if (bl != null) {
								if (bl.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + bl.y));
								} else {
									pb.lines.add(bl);
								}
							}
						}
					}
					if (rawBranch.bpm_events != null) {
						for (BpmEvent ev : rawBranch.bpm_events) {
							if (ev != null) {
								if (ev.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + ev.y));
								} else {
									pb.bpmEvents.add(ev);
								}
							}
						}
					}
					if (rawBranch.stop_events != null) {
						for (StopEvent ev : rawBranch.stop_events) {
							if (ev != null) {
								if (ev.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + ev.y));
								} else {
									pb.stopEvents.add(ev);
								}
							}
						}
					}
					if (rawBranch.scroll_events != null) {
						for (ScrollEvent ev : rawBranch.scroll_events) {
							if (ev != null) {
								if (ev.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + ev.y));
								} else {
									pb.scrollEvents.add(ev);
								}
							}
						}
					}
					if (rawBranch.speed_events != null) {
						for (ScrollEvent ev : rawBranch.speed_events) {
							if (ev != null) {
								if (ev.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + ev.y));
								} else {
									pb.speedEvents.add(ev);
								}
							}
						}
					}
					if (rawBranch.sound_channels != null) {
						for (SoundChannel sc : rawBranch.sound_channels) {
							if (sc != null) {
								final SoundChannel sanitized = copySortedSoundChannel(sc, evalY, log);
								if (sanitized.notes.length > 0) {
									final int id = wavList.size();
									wavList.add(sc.name);
									condWavIdSet.add(id);
									pb.soundChannels.add(new AssignedSoundChannel(id, sanitized));
								}
							}
						}
					}
					if (rawBranch.key_channels != null) {
						for (MineChannel mc : rawBranch.key_channels) {
							if (mc != null) {
								final MineChannel sanitized = copySortedMineChannel(mc, evalY, log);
								if (sanitized.notes.length > 0) {
									final int id = wavList.size();
									wavList.add(mc.name);
									condWavIdSet.add(id);
									pb.keyChannels.add(new AssignedMineChannel(id, sanitized));
								}
							}
						}
					}
					if (rawBranch.mine_channels != null) {
						for (MineChannel mc : rawBranch.mine_channels) {
							if (mc != null) {
								final MineChannel sanitized = copySortedMineChannel(mc, evalY, log);
								if (sanitized.notes.length > 0) {
									final int id = wavList.size();
									wavList.add(mc.name);
									condWavIdSet.add(id);
									condHasMine = true;
									pb.mineChannels.add(new AssignedMineChannel(id, sanitized));
								}
							}
						}
					}
					if (rawBranch.free_zone_channels != null) {
						for (FreeZoneChannel fzc : rawBranch.free_zone_channels) {
							if (fzc != null) {
								final FreeZoneChannel sanitized = copySortedFreeZoneChannel(fzc, evalY, log);
								if (sanitized.notes.length > 0) {
									final int id;
									if (fzc.name != null && !fzc.name.isEmpty()) {
										id = wavList.size();
										wavList.add(fzc.name);
										condWavIdSet.add(id);
									} else {
										id = -1;
									}
									for (bms.model.bmson.FreeZoneNote fn : sanitized.notes) {
										final int key = (fn.x > 0 && fn.x <= keyassign.length) ? keyassign[fn.x - 1] : -1;
										if (key >= 0 && model.getMode().isScratchKey(key)) {
											condHasFreeZone = true;
											break;
										}
									}
									pb.freeZoneChannels.add(new AssignedFreeZoneChannel(id, sanitized));
								}
							}
						}
					}
					if (rawBranch.potion_notes != null) {
						for (bms.model.bmson.PotionNote pn : rawBranch.potion_notes) {
							if (pn != null) {
								if (pn.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + pn.y));
								} else {
									pb.potionNotes.add(pn);
								}
							}
						}
					}
					if (rawBranch.dummy_notes != null) {
						for (Note dn : rawBranch.dummy_notes) {
							if (dn != null) {
								if (dn.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + dn.y));
								} else {
									pb.dummyNotes.add(dn);
								}
							}
						}
					}
					if (rawBranch.dummy_mine_notes != null) {
						for (bms.model.bmson.MineNote dmn : rawBranch.dummy_mine_notes) {
							if (dmn != null) {
								if (dmn.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + dmn.y));
								} else {
									pb.dummyMineNotes.add(dmn);
								}
							}
						}
					}
					if (rawBranch.dummy_potion_notes != null) {
						for (bms.model.bmson.MineNote dpn : rawBranch.dummy_potion_notes) {
							if (dpn != null) {
								if (dpn.y < evalY) {
									log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + dpn.y));
								} else {
									pb.dummyPotionNotes.add(dpn);
								}
							}
						}
					}
					collectBranchBgaEvents(rawBranch, evalY, pb, log);

					preparedBranches.add(pb);
					modelBranches.add(mb);
				}

				if (!hasDefault) {
					log.add(new DecodeLog(ERROR, "conditional_blocksにdefault分岐が存在しません : y=" + evalY));
					continue;
				}

				final double evalPos = evalY / resolution;
				int evalBar = (int) Math.floor(evalPos);
				double evalFraction = evalPos - evalBar;
				if (evalFraction < 1e-9) {
					evalFraction = 0.0;
				} else if (evalFraction >= 1.0 - 1e-9) {
					evalBar++;
					evalFraction = 0.0;
				}
				final int blockIndex = ctx.conditionalBlocks.size();
				final bms.model.ConditionalBlock cb = new bms.model.ConditionalBlock(
						blockIndex, evalBar, evalFraction, modelBranches);
				ctx.conditionalBlocks.add(new PreparedConditionalBlock(evalY, cb, preparedBranches));
			}
		}

		model.setWavList(wavList.toArray(new String[0]));

		// 初期TimeLine構築（すべてDEFAULT分岐）
		buildBmsonTimeLines(model, ctx, null, log);

		// conditional_blocks が存在する場合、ChartBuilderおよび全分岐事前ロード用データを設定
		if (!ctx.conditionalBlocks.isEmpty()) {
			final bms.model.ConditionalBlock[] blocksArray = new bms.model.ConditionalBlock[ctx.conditionalBlocks.size()];
			for (int i = 0; i < blocksArray.length; i++) {
				blocksArray[i] = ctx.conditionalBlocks.get(i).modelBlock;
			}
			final int[] condWavIds = condWavIdSet.stream().mapToInt(Integer::intValue).toArray();

			// 全分岐のスライス音・ノーツを事前収集する
			final List<bms.model.Note> preloadNotes = collectAllBranchPreloadNotes(model, ctx);

			final bms.model.ConditionalBlock.ChartBuilder chartBuilder = new bms.model.ConditionalBlock.ChartBuilder(
					blocksArray, model.getMode()) {
				@Override
				public void buildTimeLines(BMSModel targetModel, int[] selectedBranches, List<DecodeLog> buildLog) {
					targetModel.setMode(getResolvedMode());
					buildBmsonTimeLines(targetModel, ctx, selectedBranches, buildLog);
				}
			};

			model.setConditionalBlocks(blocksArray);
			model.setConditionalChartBuilder(chartBuilder);
			model.setConditionalWavIds(condWavIds);
			model.setConditionalPreloadNotes(preloadNotes.toArray(new bms.model.Note[0]));
			model.setConditionalContainsMineNote(condHasMine);
			model.setConditionalContainsFreeZone(condHasFreeZone);
		}

		Logger.getGlobal().fine("BMSONファイル解析完了 :" + f.toString() + " - TimeLine数:" + model.getAllTimeLines().length + " 時間(ms):"
				+ (System.currentTimeMillis() - currnttime));

		model.setChartInformation(new ChartInformation(source, lntype, null));
		printLog(f);
		return model;
	}

	private static void collectBranchBgaEvents(ConditionalBranch rawBranch, int evalY, PreparedBranch pb, List<DecodeLog> log) {
		final List<BNote[]> bgaSources = new ArrayList<>(2);
		final List<BNote[]> layerSources = new ArrayList<>(2);
		final List<BNote[]> poorSources = new ArrayList<>(2);
		if (rawBranch.bga != null) {
			if (rawBranch.bga.bga_events != null) {
				bgaSources.add(rawBranch.bga.bga_events);
			}
			if (rawBranch.bga.layer_events != null) {
				layerSources.add(rawBranch.bga.layer_events);
			}
			if (rawBranch.bga.poor_events != null) {
				poorSources.add(rawBranch.bga.poor_events);
			}
		}
		if (rawBranch.bga_events != null) {
			bgaSources.add(rawBranch.bga_events);
		}
		if (rawBranch.layer_events != null) {
			layerSources.add(rawBranch.layer_events);
		}
		if (rawBranch.poor_events != null) {
			poorSources.add(rawBranch.poor_events);
		}
		for (BNote[] arr : bgaSources) {
			for (BNote n : arr) {
				if (n != null) {
					if (n.y < evalY) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					} else {
						pb.bgaEvents.add(n);
					}
				}
			}
		}
		for (BNote[] arr : layerSources) {
			for (BNote n : arr) {
				if (n != null) {
					if (n.y < evalY) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					} else {
						pb.layerEvents.add(n);
					}
				}
			}
		}
		for (BNote[] arr : poorSources) {
			for (BNote n : arr) {
				if (n != null) {
					if (n.y < evalY) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					} else {
						pb.poorEvents.add(n);
					}
				}
			}
		}
	}

	private static SoundChannel copySortedSoundChannel(SoundChannel src, int minY, List<DecodeLog> log) {
		final SoundChannel dst = new SoundChannel();
		dst.name = src.name;
		if (src.notes == null || src.notes.length == 0) {
			dst.notes = new Note[0];
			return dst;
		}
		final List<Note> valid = new ArrayList<>(src.notes.length);
		for (Note n : src.notes) {
			if (n != null) {
				if (n.y < minY) {
					if (log != null) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					}
				} else {
					valid.add(n);
				}
			}
		}
		valid.sort(COMPARATOR);
		dst.notes = valid.toArray(new Note[0]);
		return dst;
	}

	private static MineChannel copySortedMineChannel(MineChannel src, int minY, List<DecodeLog> log) {
		final MineChannel dst = new MineChannel();
		dst.name = src.name;
		if (src.notes == null || src.notes.length == 0) {
			dst.notes = new bms.model.bmson.MineNote[0];
			return dst;
		}
		final List<bms.model.bmson.MineNote> valid = new ArrayList<>(src.notes.length);
		for (bms.model.bmson.MineNote n : src.notes) {
			if (n != null) {
				if (n.y < minY) {
					if (log != null) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					}
				} else {
					valid.add(n);
				}
			}
		}
		valid.sort(COMPARATOR);
		dst.notes = valid.toArray(new bms.model.bmson.MineNote[0]);
		return dst;
	}

	private static FreeZoneChannel copySortedFreeZoneChannel(FreeZoneChannel src, int minY, List<DecodeLog> log) {
		final FreeZoneChannel dst = new FreeZoneChannel();
		dst.name = src.name;
		if (src.notes == null || src.notes.length == 0) {
			dst.notes = new bms.model.bmson.FreeZoneNote[0];
			return dst;
		}
		final List<bms.model.bmson.FreeZoneNote> valid = new ArrayList<>(src.notes.length);
		for (bms.model.bmson.FreeZoneNote n : src.notes) {
			if (n != null) {
				if (n.y < minY) {
					if (log != null) {
						log.add(new DecodeLog(WARNING, "conditional_blocksの評価位置より前の定義は無視されます - y : " + n.y));
					}
				} else {
					valid.add(n);
				}
			}
		}
		valid.sort(COMPARATOR);
		dst.notes = valid.toArray(new bms.model.bmson.FreeZoneNote[0]);
		return dst;
	}

	/**
	 * 全分岐のスライス音を含む事前ロード対象ノーツを収集する
	 */
	private static List<bms.model.Note> collectAllBranchPreloadNotes(BMSModel baseModel, BmsonBuildContext ctx) {
		final List<bms.model.Note> preload = new ArrayList<>();
		final int numBlocks = ctx.conditionalBlocks.size();
		for (int bIdx = 0; bIdx < numBlocks; bIdx++) {
			final PreparedConditionalBlock pcb = ctx.conditionalBlocks.get(bIdx);
			final int defaultIdx = pcb.modelBlock.getDefaultBranchIndex();
			for (int brIdx = 0; brIdx < pcb.branches.size(); brIdx++) {
				if (brIdx == defaultIdx) {
					continue;
				}
				final int[] sel = new int[numBlocks];
				for (int k = 0; k < numBlocks; k++) {
					sel[k] = (k == bIdx) ? brIdx : ctx.conditionalBlocks.get(k).modelBlock.getDefaultBranchIndex();
				}
				final BMSModel tempModel = new BMSModel();
				tempModel.setBpm(baseModel.getBpm());
				tempModel.setMode(baseModel.getMode());
				tempModel.setLnmode(baseModel.getLnmode());
				final List<DecodeLog> dummyLog = new ArrayList<>();
				buildBmsonTimeLines(tempModel, ctx, sel, dummyLog);
				final int keys = tempModel.getMode().key;
				for (TimeLine tl : tempModel.getAllTimeLines()) {
					for (int lane = 0; lane < keys; lane++) {
						final bms.model.Note n = tl.getNote(lane);
						if (n != null && !n.isDummy()) {
							if (n.getWav() >= 0) {
								preload.add(n);
							}
							for (bms.model.Note layered : n.getLayeredNotes()) {
								if (layered.getWav() >= 0) {
									preload.add(layered);
								}
							}
						}
						final bms.model.Note hn = tl.getHiddenNote(lane);
						if (hn != null && hn.getWav() >= 0) {
							preload.add(hn);
						}
					}
					for (bms.model.Note bg : tl.getBackGroundNotes()) {
						if (bg != null && bg.getWav() >= 0) {
							preload.add(bg);
						}
					}
				}
			}
		}
		return preload;
	}

	/**
	 * 指定された採択分岐配列に基づいて BMSModel の TimeLine 群を構築する
	 */
	@SuppressWarnings("unchecked")
	private static void buildBmsonTimeLines(
			BMSModel targetModel,
			BmsonBuildContext ctx,
			int[] selectedBranches,
			List<DecodeLog> log) {
		final double resolution = ctx.resolution;
		final int quarterPulse = ctx.quarterPulse;
		final int[] keyassign = ctx.keyassign;
		final int numKeys = targetModel.getMode().key;

		// 各conditional_blockで選択されたPreparedBranchを決定
		final List<PreparedBranch> activeBranches = new ArrayList<>(ctx.conditionalBlocks.size());
		for (int i = 0; i < ctx.conditionalBlocks.size(); i++) {
			final PreparedConditionalBlock pcb = ctx.conditionalBlocks.get(i);
			final int branchCount = pcb.branches.size();
			final int chosen = (selectedBranches != null
					&& i < selectedBranches.length
					&& selectedBranches[i] >= 0
					&& selectedBranches[i] < branchCount)
							? selectedBranches[i]
							: pcb.modelBlock.getDefaultBranchIndex();
			activeBranches.add(pcb.branches.get(chosen));
		}

		final TreeMap<Integer, TimeLineCache> localTlCache = new TreeMap<>();
		final TimeLine basetl = new TimeLine(0, 0, numKeys);
		basetl.setBPM(targetModel.getBpm());
		localTlCache.put(0, new TimeLineCache(0.0, basetl));

		// 1. BPM / STOP / SCROLL / SPEED イベントの合成・ソート・適用
		final List<BpmEvent> bpmList = new ArrayList<>(ctx.rootBpmEvents);
		final List<StopEvent> stopList = new ArrayList<>(ctx.rootStopEvents);
		final List<ScrollEvent> scrollList = new ArrayList<>(ctx.rootScrollEvents);
		final List<ScrollEvent> speedList = new ArrayList<>(ctx.rootSpeedEvents);
		for (PreparedBranch pb : activeBranches) {
			bpmList.addAll(pb.bpmEvents);
			stopList.addAll(pb.stopEvents);
			scrollList.addAll(pb.scrollEvents);
			speedList.addAll(pb.speedEvents);
		}
		final BpmEvent[] bpmEvents = bpmList.toArray(new BpmEvent[0]);
		final StopEvent[] stopEvents = stopList.toArray(new StopEvent[0]);
		final ScrollEvent[] scrollEvents = scrollList.toArray(new ScrollEvent[0]);
		final ScrollEvent[] speedEvents = speedList.toArray(new ScrollEvent[0]);
		Arrays.sort(bpmEvents, COMPARATOR);
		Arrays.sort(stopEvents, COMPARATOR);
		Arrays.sort(scrollEvents, COMPARATOR);
		Arrays.sort(speedEvents, COMPARATOR);

		int bpmpos = 0;
		int stoppos = 0;
		int scrollpos = 0;
		int speedpos = 0;
		while (bpmpos < bpmEvents.length || stoppos < stopEvents.length || scrollpos < scrollEvents.length || speedpos < speedEvents.length) {
			final int bpmy = bpmpos < bpmEvents.length ? bpmEvents[bpmpos].y : Integer.MAX_VALUE;
			final int stopy = stoppos < stopEvents.length ? stopEvents[stoppos].y : Integer.MAX_VALUE;
			final int scrolly = scrollpos < scrollEvents.length ? scrollEvents[scrollpos].y : Integer.MAX_VALUE;
			final int speedy = speedpos < speedEvents.length ? speedEvents[speedpos].y : Integer.MAX_VALUE;
			if (speedy <= scrolly && speedy <= stopy && speedy <= bpmy) {
				getTimeLine(localTlCache, targetModel, speedy, resolution).setSpeedObj(speedEvents[speedpos].rate);
				speedpos++;
			} else if (scrolly <= stopy && scrolly <= bpmy) {
				getTimeLine(localTlCache, targetModel, scrolly, resolution).setScroll(scrollEvents[scrollpos].rate);
				scrollpos++;
			} else if (bpmy <= stopy) {
				if (bpmEvents[bpmpos].bpm > 0) {
					getTimeLine(localTlCache, targetModel, bpmy, resolution).setBPM(bpmEvents[bpmpos].bpm);
				} else {
					log.add(new DecodeLog(WARNING,
							"negative BPMはサポートされていません - y : " + bpmEvents[bpmpos].y + " bpm : " + bpmEvents[bpmpos].bpm));
				}
				bpmpos++;
			} else if (stopy != Integer.MAX_VALUE) {
				if (stopEvents[stoppos].duration >= 0) {
					final TimeLine tl = getTimeLine(localTlCache, targetModel, stopy, resolution);
					tl.setStop((long) ((1000.0 * 1000 * 60 * 4 * stopEvents[stoppos].duration)
							/ (tl.getBPM() * resolution)));
				} else {
					log.add(new DecodeLog(WARNING,
							"negative STOPはサポートされていません - y : " + stopEvents[stoppos].y + " bpm : " + stopEvents[stoppos].duration));
				}
				stoppos++;
			}
		}

		// 2. lines（小節線）の処理
		for (BarLine bl : ctx.rootLines) {
			getTimeLine(localTlCache, targetModel, bl.y, resolution).setSectionLine(true);
		}
		for (PreparedBranch pb : activeBranches) {
			for (BarLine bl : pb.lines) {
				getTimeLine(localTlCache, targetModel, bl.y, resolution).setSectionLine(true);
			}
		}

		final List<LongNote>[] lnlist = new List[numKeys];
		final Map<Note, LongNote> lnup = new HashMap<>();

		// 3. sound_channels（本物のLN・通常ノーツ・BGM・LN終端音）の処理
		final List<AssignedSoundChannel> soundChannels = new ArrayList<>(ctx.rootSoundChannels);
		for (PreparedBranch pb : activeBranches) {
			soundChannels.addAll(pb.soundChannels);
		}
		for (AssignedSoundChannel asc : soundChannels) {
			final int id = asc.wavId;
			final SoundChannel sc = asc.channel;
			final int length = sc.notes.length;
			long starttime = 0;
			for (int i = 0; i < length; i++) {
				final Note n = sc.notes[i];
				Note next = null;
				for (int j = i + 1; j < length; j++) {
					if (sc.notes[j].y > n.y) {
						next = sc.notes[j];
						break;
					}
				}
				long duration = 0;
				if (!n.c) {
					starttime = 0;
				}
				final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
				if (next != null && next.c) {
					duration = getTimeLine(localTlCache, targetModel, next.y, resolution).getMicroTime() - tl.getMicroTime();
				}

				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key < 0) {
					// BGノート
					tl.addBackGroundNote(new NormalNote(id, starttime, duration));
				} else if (n.up) {
					// LN終端音定義
					boolean assigned = false;
					if (lnlist[key] != null) {
						final double section = (n.y / resolution);
						for (LongNote ln : lnlist[key]) {
							if (section == ln.getPair().getSection()) {
								ln.getPair().setWav(id);
								ln.getPair().setMicroStarttime(starttime);
								ln.getPair().setMicroDuration(duration);
								assigned = true;
								break;
							}
						}
					}
					if (!assigned) {
						lnup.put(n, new LongNote(id, starttime, duration));
					}
				} else {
					boolean insideln = false;
					if (lnlist[key] != null) {
						final double section = (n.y / resolution);
						for (LongNote ln : lnlist[key]) {
							if (ln.getSection() < section && section <= ln.getPair().getSection()) {
								insideln = true;
								break;
							}
						}
					}

					if (insideln) {
						log.add(new DecodeLog(WARNING,
								"LN内にノートを定義しています - x :  " + n.x + " y : " + n.y));
						tl.addBackGroundNote(new NormalNote(id, starttime, duration));
					} else {
						if (n.l > 0) {
							// ロングノート
							final TimeLine end = getTimeLine(localTlCache, targetModel, n.y + n.l, resolution);
							final LongNote ln = new LongNote(id, starttime, duration);
							if (tl.getNote(key) != null) {
								// レイヤーノート判定
								final bms.model.Note en = tl.getNote(key);
								if (en instanceof LongNote && end.getNote(key) == ((LongNote) en).getPair()) {
									en.addLayeredNote(ln);
								} else {
									log.add(new DecodeLog(WARNING,
											"同一の位置にノートが複数定義されています - x :  " + n.x + " y : " + n.y));
								}
							} else {
								boolean existNote = false;
								for (TimeLineCache tl2 : localTlCache.subMap(n.y, false, n.y + n.l, true).values()) {
									if (tl2.timeline.existNote(key)) {
										existNote = true;
										break;
									}
								}
								if (existNote) {
									log.add(new DecodeLog(WARNING,
											"LN内にノートを定義しています - x :  " + n.x + " y : " + n.y));
									tl.addBackGroundNote(new NormalNote(id, starttime, duration));
								} else {
									tl.setNote(key, ln);
									LongNote lnend = null;
									for (Entry<Note, LongNote> up : lnup.entrySet()) {
										if (up.getKey().y == n.y + n.l && up.getKey().x == n.x) {
											lnend = up.getValue();
											break;
										}
									}
									if (lnend == null) {
										lnend = new LongNote(-2);
									}

									end.setNote(key, lnend);
									ln.setType(n.t > 0 && n.t <= 4 ? n.t : targetModel.getLnmode());
									ln.setPair(lnend);
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<>();
									}
									lnlist[key].add(ln);
								}
							}
						} else {
							// 通常ノート
							if (tl.existNote(key)) {
								if (tl.getNote(key) instanceof NormalNote) {
									tl.getNote(key).addLayeredNote(new NormalNote(id, starttime, duration));
								} else {
									log.add(new DecodeLog(WARNING,
											"同一の位置にノートが複数定義されています - x :  " + n.x + " y : " + n.y));
								}
							} else {
								tl.setNote(key, new NormalNote(id, starttime, duration));
							}
						}
					}
				}
				starttime += duration;
			}
		}

		// 4. key_channels（不可視キー音）の処理
		final List<AssignedMineChannel> keyChannels = new ArrayList<>(ctx.rootKeyChannels);
		for (PreparedBranch pb : activeBranches) {
			keyChannels.addAll(pb.keyChannels);
		}
		for (AssignedMineChannel amc : keyChannels) {
			final int id = amc.wavId;
			final MineChannel sc = amc.channel;
			for (bms.model.bmson.MineNote n : sc.notes) {
				final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key >= 0) {
					tl.setHiddenNote(key, new NormalNote(id));
				}
			}
		}

		// 5. mine_channels（本物の地雷ノーツ）の処理
		final List<AssignedMineChannel> mineChannels = new ArrayList<>(ctx.rootMineChannels);
		for (PreparedBranch pb : activeBranches) {
			mineChannels.addAll(pb.mineChannels);
		}
		for (AssignedMineChannel amc : mineChannels) {
			final int id = (amc.channel.name != null && !amc.channel.name.isEmpty()) ? amc.wavId : -2;
			final MineChannel sc = amc.channel;
			for (bms.model.bmson.MineNote n : sc.notes) {
				final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key >= 0) {
					final boolean insideln = isInsideExistingLongNote(lnlist[key], n.y / resolution);
					if (insideln) {
						log.add(new DecodeLog(WARNING,
								"LN内に地雷ノートを定義しています - x :  " + n.x + " y : " + n.y));
					} else if (tl.existNote(key)) {
						log.add(new DecodeLog(WARNING,
								"地雷ノートを定義している位置に通常ノートが存在します - x :  " + n.x + " y : " + n.y));
					} else {
						tl.setNote(key, new bms.model.MineNote(id, n.damage));
					}
				}
			}
		}

		// 6. potion_notes（本物のポーションノーツ）の処理
		final List<bms.model.bmson.PotionNote> potionNotes = new ArrayList<>(ctx.rootPotionNotes);
		for (PreparedBranch pb : activeBranches) {
			potionNotes.addAll(pb.potionNotes);
		}
		potionNotes.sort(COMPARATOR);
		for (bms.model.bmson.PotionNote n : potionNotes) {
			if (n.recovery <= 0) {
				continue;
			}
			final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
			if (key >= 0) {
				final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
				final boolean conflict = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], n.y / resolution);
				if (conflict) {
					log.add(new DecodeLog(WARNING,
							"ポーションノート追加時に衝突が発生しました - x : " + n.x + " y : " + n.y));
				} else {
					tl.setNote(key, new bms.model.PotionNote(-1, n.recovery));
				}
			}
		}

		// 7. dummy_notes（ダミーLN l > 0 → ダミー通常ノーツ l <= 0）の処理
		final List<Note> dummyNotes = new ArrayList<>(ctx.rootDummyNotes);
		for (PreparedBranch pb : activeBranches) {
			dummyNotes.addAll(pb.dummyNotes);
		}
		dummyNotes.sort(COMPARATOR);
		// 7a. ダミーLN (l > 0)
		for (Note n : dummyNotes) {
			if (n.l <= 0) {
				continue;
			}
			final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
			if (key < 0) {
				continue;
			}
			final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
			final TimeLine end = getTimeLine(localTlCache, targetModel, n.y + n.l, resolution);
			boolean conflict = isOverlappingExistingLongNote(lnlist[key], tl.getSection(), end.getSection());
			if (!conflict) {
				for (TimeLineCache tl2 : localTlCache.subMap(n.y, true, n.y + n.l, true).values()) {
					if (tl2.timeline.existNote(key)) {
						conflict = true;
						break;
					}
				}
			}
			if (conflict) {
				log.add(new DecodeLog(WARNING,
						"ダミーLN追加時に衝突が発生しました - x : " + n.x + " y : " + n.y));
			} else {
				final LongNote ln = new LongNote(-2);
				ln.setDummy(true);
				ln.setType(n.t >= 1 && n.t <= 4 ? n.t : targetModel.getLnmode());
				final LongNote lnend = new LongNote(-2);
				lnend.setDummy(true);
				tl.setNote(key, ln);
				end.setNote(key, lnend);
				ln.setPair(lnend);
				if (lnlist[key] == null) {
					lnlist[key] = new ArrayList<>();
				}
				lnlist[key].add(ln);
			}
		}
		// 7b. ダミー通常ノーツ (l <= 0)
		for (Note n : dummyNotes) {
			if (n.l > 0) {
				continue;
			}
			final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
			if (key < 0) {
				continue;
			}
			final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
			final boolean conflict = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection());
			if (conflict) {
				log.add(new DecodeLog(WARNING,
						"ダミー通常ノート追加時に衝突が発生しました - x : " + n.x + " y : " + n.y));
			} else {
				final NormalNote dn = new NormalNote(-1);
				dn.setDummy(true);
				tl.setNote(key, dn);
			}
		}

		// 8. dummy_mine_notes（ダミー地雷）および dummy_potion_notes（ダミーポーション）の処理
		final List<bms.model.bmson.MineNote> dummyMineNotes = new ArrayList<>(ctx.rootDummyMineNotes);
		final List<bms.model.bmson.MineNote> dummyPotionNotes = new ArrayList<>(ctx.rootDummyPotionNotes);
		for (PreparedBranch pb : activeBranches) {
			dummyMineNotes.addAll(pb.dummyMineNotes);
			dummyPotionNotes.addAll(pb.dummyPotionNotes);
		}
		dummyMineNotes.sort(COMPARATOR);
		dummyPotionNotes.sort(COMPARATOR);
		for (int noteType = 0; noteType < 2; noteType++) {
			final List<bms.model.bmson.MineNote> list = (noteType == 0) ? dummyMineNotes : dummyPotionNotes;
			for (bms.model.bmson.MineNote n : list) {
				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key < 0) {
					continue;
				}
				final TimeLine tl = getTimeLine(localTlCache, targetModel, n.y, resolution);
				final boolean conflict = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection());
				if (conflict) {
					log.add(new DecodeLog(WARNING,
							(noteType == 0 ? "ダミー地雷ノート" : "ダミーポーションノート")
									+ "追加時に衝突が発生しました - x : " + n.x + " y : " + n.y));
				} else {
					final bms.model.Note dn = (noteType == 0)
							? new bms.model.MineNote(-1, 0)
							: new bms.model.PotionNote(-1, 0);
					dn.setDummy(true);
					tl.setNote(key, dn);
				}
			}
		}

		// 9. free_zone_channels（フリーゾーン）の処理
		final List<AssignedFreeZoneChannel> freeZoneChannels = new ArrayList<>(ctx.rootFreeZoneChannels);
		for (PreparedBranch pb : activeBranches) {
			freeZoneChannels.addAll(pb.freeZoneChannels);
		}
		final Map<Integer, List<int[]>> intervalsByLane = new TreeMap<>();
		for (AssignedFreeZoneChannel afzc : freeZoneChannels) {
			final int wavId = afzc.wavId;
			final FreeZoneChannel fzc = afzc.channel;
			for (bms.model.bmson.FreeZoneNote n : fzc.notes) {
				final int key = n.x > 0 && n.x <= keyassign.length ? keyassign[n.x - 1] : -1;
				if (key < 0 || !targetModel.getMode().isScratchKey(key)) {
					log.add(new DecodeLog(WARNING,
							"フリーゾーンの対象レーンがスクラッチではありません - x : " + n.x + " y : " + n.y));
					continue;
				}
				final int startY = n.y;
				final int durationPulse = n.l > 0 ? n.l : quarterPulse;
				final int endY = startY + durationPulse;
				final TimeLine startTl = getTimeLine(localTlCache, targetModel, startY, resolution);
				getTimeLine(localTlCache, targetModel, endY, resolution);
				if (wavId >= 0) {
					startTl.addBackGroundNote(new NormalNote(wavId));
				}
				intervalsByLane.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[] { startY, endY });
			}
		}
		for (Entry<Integer, List<int[]>> entry : intervalsByLane.entrySet()) {
			applyMergedFreeZonesPulse(localTlCache, targetModel, resolution, entry.getKey(), entry.getValue());
		}

		// 10. BGAイベントの処理
		final List<BNote> bgaEvents = new ArrayList<>(ctx.rootBgaEvents);
		final List<BNote> layerEvents = new ArrayList<>(ctx.rootLayerEvents);
		final List<BNote> poorEvents = new ArrayList<>(ctx.rootPoorEvents);
		for (PreparedBranch pb : activeBranches) {
			bgaEvents.addAll(pb.bgaEvents);
			layerEvents.addAll(pb.layerEvents);
			poorEvents.addAll(pb.poorEvents);
		}
		for (BNote n : bgaEvents) {
			getTimeLine(localTlCache, targetModel, n.y, resolution).setBGA(resolveBgaId(ctx.bgaIdMap, n.id, log));
		}
		for (BNote n : layerEvents) {
			final int[] idset = n.id_set != null ? n.id_set : new int[] { n.id };
			final Layer.Sequence[][] seqs = new Layer.Sequence[idset.length][];
			final Layer.Event event;
			switch (n.condition != null ? n.condition : "") {
			case "play":
				event = new Layer.Event(EventType.PLAY, n.interval);
				break;
			case "miss":
				event = new Layer.Event(EventType.MISS, n.interval);
				break;
			default:
				event = new Layer.Event(EventType.ALWAYS, n.interval);
			}
			for (int seqindex = 0; seqindex < seqs.length; seqindex++) {
				final int nid = idset[seqindex];
				if (ctx.bgaSeqMap.containsKey(nid)) {
					seqs[seqindex] = ctx.bgaSeqMap.get(nid);
				} else {
					seqs[seqindex] = new Layer.Sequence[] {
							new Layer.Sequence(0, resolveBgaId(ctx.bgaIdMap, nid, log)),
							new Layer.Sequence(500)
					};
				}
			}
			getTimeLine(localTlCache, targetModel, n.y, resolution).setEventlayer(new Layer[] { new Layer(event, seqs) });
		}
		for (BNote n : poorEvents) {
			if (ctx.bgaSeqMap.containsKey(n.id)) {
				getTimeLine(localTlCache, targetModel, n.y, resolution).setEventlayer(new Layer[] {
						new Layer(new Layer.Event(EventType.MISS, 1), new Layer.Sequence[][] { ctx.bgaSeqMap.get(n.id) })
				});
			} else {
				getTimeLine(localTlCache, targetModel, n.y, resolution).setEventlayer(new Layer[] {
						new Layer(new Layer.Event(EventType.MISS, 1),
								new Layer.Sequence[][] { { new Layer.Sequence(0, resolveBgaId(ctx.bgaIdMap, n.id, log)), new Layer.Sequence(500) } })
				});
			}
		}

		// 11. conditional_blocks の評価TimeLine生成
		if (!ctx.conditionalBlocks.isEmpty()) {
			final TimeLine[] evalTimeLines = new TimeLine[ctx.conditionalBlocks.size()];
			for (int i = 0; i < ctx.conditionalBlocks.size(); i++) {
				evalTimeLines[i] = getTimeLine(localTlCache, targetModel, ctx.conditionalBlocks.get(i).evalY, resolution);
			}
			targetModel.setConditionalEvalTimeLines(evalTimeLines);
		}

		targetModel.setAllTimeLine(
				localTlCache.values().stream()
						.map(tlc -> tlc.timeline)
						.collect(Collectors.toList())
						.toArray(new TimeLine[localTlCache.size()]));
	}

	private static boolean isInsideExistingLongNote(List<LongNote> lns, double section) {
		if (lns == null) {
			return false;
		}
		for (LongNote ln : lns) {
			if (ln.getPair() != null && ln.getSection() <= section && section <= ln.getPair().getSection()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOverlappingExistingLongNote(List<LongNote> lns, double startSection, double endSection) {
		if (lns == null) {
			return false;
		}
		for (LongNote ln : lns) {
			if (ln.getPair() != null && ln.getSection() <= endSection && startSection <= ln.getPair().getSection()) {
				return true;
			}
		}
		return false;
	}

	private static void applyMergedFreeZonesPulse(
			TreeMap<Integer, TimeLineCache> localTlCache,
			BMSModel targetModel,
			double resolution,
			int lane,
			List<int[]> intervals) {
		if (intervals.isEmpty()) {
			return;
		}
		intervals.sort((a, b) -> {
			int cmp = Integer.compare(a[0], b[0]);
			return cmp != 0 ? cmp : Integer.compare(a[1], b[1]);
		});
		final List<int[]> merged = new ArrayList<>();
		int curStart = intervals.get(0)[0];
		int curEnd = intervals.get(0)[1];
		for (int i = 1; i < intervals.size(); i++) {
			final int nextStart = intervals.get(i)[0];
			final int nextEnd = intervals.get(i)[1];
			if (nextStart <= curEnd) {
				if (nextEnd > curEnd) {
					curEnd = nextEnd;
				}
			} else {
				merged.add(new int[] { curStart, curEnd });
				curStart = nextStart;
				curEnd = nextEnd;
			}
		}
		merged.add(new int[] { curStart, curEnd });

		for (int[] range : merged) {
			final TimeLine startTl = getTimeLine(localTlCache, targetModel, range[0], resolution);
			final TimeLine endTl = getTimeLine(localTlCache, targetModel, range[1], resolution);
			final List<bms.model.Note> innerList = new ArrayList<>();
			for (TimeLineCache tlc : localTlCache.subMap(range[0], true, range[1], true).values()) {
				final bms.model.Note n = tlc.timeline.getNote(lane);
				if (n != null && n.isDummy()) {
					continue;
				}
				if (n instanceof NormalNote normalNote) {
					normalNote.setFreeZoneInner(true);
					innerList.add(normalNote);
				} else if (n instanceof LongNote ln) {
					final LongNote lnStart = ln.isEnd() ? ln.getPair() : ln;
					final LongNote lnEnd = ln.isEnd() ? ln : ln.getPair();
					if (lnStart != null && lnEnd != null && !lnStart.isFreeZoneInner()) {
						lnStart.setFreeZoneInner(true);
						lnEnd.setFreeZoneInner(true);
						innerList.add(lnStart);
					}
				}
			}
			final bms.model.FreeZoneNote fzStart = new bms.model.FreeZoneNote();
			final bms.model.FreeZoneNote fzEnd = new bms.model.FreeZoneNote();
			startTl.setFreeZone(lane, fzStart);
			endTl.setFreeZone(lane, fzEnd);
			fzStart.setPair(fzEnd);
			fzStart.setInnerNotes(innerList.toArray(new bms.model.Note[0]));
		}
	}

	private static TimeLine getTimeLine(TreeMap<Integer, TimeLineCache> cache, BMSModel targetModel, int y, double resolution) {
		final TimeLineCache tlc = cache.get(y);
		if (tlc != null) {
			return tlc.timeline;
		}

		final Entry<Integer, TimeLineCache> le = cache.lowerEntry(y);
		final double bpm = le.getValue().timeline.getBPM();
		final double scroll = le.getValue().timeline.getScroll();
		final double speed = le.getValue().timeline.getSpeed();
		final double time = le.getValue().time + le.getValue().timeline.getMicroStop()
				+ (240000.0 * 1000 * ((y - le.getKey()) / resolution)) / bpm;

		final TimeLine tl = new TimeLine(y / resolution, (long) time, targetModel.getMode().key);
		tl.setBPM(bpm);
		tl.setScroll(scroll);
		tl.setSpeed(speed);
		cache.put(y, new TimeLineCache(time, tl));
		return tl;
	}

	private boolean validate(Bmson bmson) {
		if (bmson == null || bmson.info == null) {
			return invalid("infoが定義されていません");
		}
		if (bmson.info.subartists == null) {
			bmson.info.subartists = new String[0];
		}
		if (bmson.lines == null) {
			bmson.lines = new BarLine[0];
		}
		if (bmson.bpm_events == null) {
			bmson.bpm_events = new BpmEvent[0];
		}
		if (bmson.stop_events == null) {
			bmson.stop_events = new StopEvent[0];
		}
		if (bmson.scroll_events == null) {
			bmson.scroll_events = new ScrollEvent[0];
		}
		if (bmson.sound_channels == null) {
			bmson.sound_channels = new SoundChannel[0];
		}
		if (bmson.key_channels == null) {
			bmson.key_channels = new MineChannel[0];
		}
		if (bmson.mine_channels == null) {
			bmson.mine_channels = new MineChannel[0];
		}

		for (BarLine line : bmson.lines) {
			if (line == null || !validY(line.y)) {
				return invalid("linesに不正な要素があります");
			}
		}
		if (!validEvents(bmson.bpm_events, "bpm_events")
				|| !validEvents(bmson.stop_events, "stop_events")
				|| !validEvents(bmson.scroll_events, "scroll_events")
				|| !validEvents(bmson.speed_events, "speed_events")
				|| !validEvents(bmson.potion_notes, "potion_notes")
				|| !validEvents(bmson.dummy_notes, "dummy_notes")
				|| !validEvents(bmson.dummy_mine_notes, "dummy_mine_notes")
				|| !validEvents(bmson.dummy_potion_notes, "dummy_potion_notes")) {
			return false;
		}
		if (bmson.dummy_notes != null) {
			for (Note note : bmson.dummy_notes) {
				if (note.l > 0 && (long) note.y + note.l >= Integer.MAX_VALUE) {
					return invalid("ロングノートの終端座標が範囲外です");
				}
			}
		}
		for (SoundChannel channel : bmson.sound_channels) {
			if (channel == null) {
				return invalid("sound_channelsにnull要素があります");
			}
			if (channel.notes == null) {
				channel.notes = new Note[0];
			}
			if (!validEvents(channel.notes, "sound_channels.notes")) {
				return false;
			}
			for (Note note : channel.notes) {
				if (note.l > 0 && (long) note.y + note.l >= Integer.MAX_VALUE) {
					return invalid("ロングノートの終端座標が範囲外です");
				}
			}
		}
		for (MineChannel channel : bmson.key_channels) {
			if (!validMineChannel(channel, "key_channels")) {
				return false;
			}
		}
		for (MineChannel channel : bmson.mine_channels) {
			if (!validMineChannel(channel, "mine_channels")) {
				return false;
			}
		}
		if (bmson.free_zone_channels != null) {
			for (FreeZoneChannel channel : bmson.free_zone_channels) {
				if (channel == null) {
					return invalid("free_zone_channelsにnull要素があります");
				}
				if (channel.notes == null) {
					channel.notes = new bms.model.bmson.FreeZoneNote[0];
				}
				if (!validEvents(channel.notes, "free_zone_channels.notes")) {
					return false;
				}
				for (bms.model.bmson.FreeZoneNote note : channel.notes) {
					if (note.l > 0 && (long) note.y + note.l >= Integer.MAX_VALUE) {
						return invalid("フリーゾーンの終端座標が範囲外です");
					}
				}
			}
		}

		if (bmson.bga != null) {
			if (bmson.bga.bga_header != null) {
				for (BGAHeader header : bmson.bga.bga_header) {
					if (header == null) {
						return invalid("bga_headerにnull要素があります");
					}
				}
			}
			if (bmson.bga.bga_sequence != null) {
				for (BGASequence sequence : bmson.bga.bga_sequence) {
					if (sequence == null) {
						continue;
					}
					if (sequence.sequence == null) {
						return invalid("bga_sequence.sequenceがnullです");
					}
					for (Sequence item : sequence.sequence) {
						if (item == null) {
							return invalid("bga_sequence.sequenceにnull要素があります");
						}
					}
				}
			}
			if (!validEvents(bmson.bga.bga_events, "bga_events")
					|| !validEvents(bmson.bga.layer_events, "layer_events")
					|| !validEvents(bmson.bga.poor_events, "poor_events")) {
				return false;
			}
		}
		return true;
	}

	private boolean validMineChannel(MineChannel channel, String name) {
		if (channel == null) {
			return invalid(name + "にnull要素があります");
		}
		if (channel.notes == null) {
			channel.notes = new bms.model.bmson.MineNote[0];
		}
		return validEvents(channel.notes, name + ".notes");
	}

	private boolean validEvents(BMSONObject[] events, String name) {
		if (events == null) {
			return true;
		}
		for (BMSONObject event : events) {
			if (event == null || !validY(event.y)) {
				return invalid(name + "に不正な要素があります");
			}
		}
		return true;
	}

	private boolean validY(int y) {
		// イベント処理では Integer.MAX_VALUE を終端の番兵として使う。
		return y >= 0 && y < Integer.MAX_VALUE;
	}

	private boolean invalid(String message) {
		log.add(new DecodeLog(ERROR, message));
		return false;
	}

	private static int resolveBgaId(Map<Integer, Integer> idmap, int id, List<DecodeLog> log) {
		Integer index = idmap.get(id);
		if (index == null) {
			log.add(new DecodeLog(WARNING, "未定義のBGA IDを参照しています : " + id));
			return -1;
		}
		return index;
	}
}
