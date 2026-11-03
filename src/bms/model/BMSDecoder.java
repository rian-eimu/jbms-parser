package bms.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static bms.model.DecodeLog.State.*;

/**
 * BMSファイルをBMSModelにデコードするクラス
 * 
 * @author exch
 */
public class BMSDecoder extends ChartDecoder {

	final List<String> wavlist = new ArrayList<String>(62 * 62);
	private final int[] wm = new int[62 * 62];

	final List<String> bgalist = new ArrayList<String>(62 * 62);
	private final int[] bm = new int[62 * 62];

	public BMSDecoder() {
		this(BMSModel.LNTYPE_LONGNOTE);
	}

	public BMSDecoder(int lntype) {
		this.lntype = lntype;
		// 予約語の登録
	}

	public BMSModel decode(Path f) {
		Logger.getGlobal().fine("BMSファイル解析開始 :" + f.toString());
		try {
			BMSModel model = this.decode(f, Files.readAllBytes(f), f.toString().toLowerCase().endsWith(".pms"), null);
			if (model == null) {
				return null;
			}
			Logger.getGlobal().fine("BMSファイル解析完了 :" + f.toString() + " - TimeLine数:" + model.getAllTimes().length);
			return model;
		} catch (IOException e) {
			log.add(new DecodeLog(ERROR, "BMSファイルが見つかりません"));
			Logger.getGlobal().severe("BMSファイル解析中の例外 : " + e.getClass().getName() + " - " + e.getMessage());
		}
		return null;
	}
	
	public BMSModel decode(ChartInformation info) {
		try {
			this.lntype = info.lntype;
			ChartSource source = info.source != null ? info.source : (info.path != null ? new PathChartSource(info.path) : null);
			if (source == null) {
				return null;
			}
			byte[] data;
			try (InputStream is = source.openStream()) {
				data = is.readAllBytes();
			}
			return decode(source, data, source.location().toLowerCase().endsWith(".pms"), info.selectedRandoms);
		} catch (IOException e) {
			log.add(new DecodeLog(ERROR, "BMSファイルが見つかりません"));
			Logger.getGlobal().severe("BMSファイル解析中の例外 : " + e.getClass().getName() + " - " + e.getMessage());
		}
		return null;
	}


	private final List<String>[] lines = new List[1000];

	private final Map<Integer, Double> scrolltable = new TreeMap<Integer, Double>();
	private final Map<Integer, Double> stoptable = new TreeMap<Integer, Double>();
	private final Map<Integer, Double> bpmtable = new TreeMap<Integer, Double>();
	private final Map<Integer, Double> speedtable = new TreeMap<Integer, Double>();
	private final Deque<Integer> randoms = new ArrayDeque<Integer>();
	private final Deque<Integer> srandoms = new ArrayDeque<Integer>();
	private final Deque<Integer> crandom = new ArrayDeque<Integer>();
	private final Deque<Boolean> skip = new ArrayDeque<Boolean>();
	
	private static final CommandWord[] commandWords = CommandWord.values();

	public static final long LEGACY_FREEZONE_CUTOFF_MILLIS = java.time.ZonedDateTime
			.of(2000, 12, 31, 23, 59, 59, 999_000_000, java.time.ZoneId.systemDefault())
			.toInstant().toEpochMilli();

	/**
	 * 指定したBMSファイルをモデルにデコードする
	 *
	 * @param data
	 * @return
	 */
	public BMSModel decode(byte[] data, boolean ispms, int[] random) {
		return this.decode((ChartSource) null, data, ispms, random);
	}
	
	/**
	 * 指定したBMSファイルをモデルにデコードする
	 *
	 * @param data
	 * @return
	 */
	private BMSModel decode(Path path, byte[] data, boolean ispms, int[] selectedRandom) {
		return decode(path != null ? new PathChartSource(path) : null, data, ispms, selectedRandom);
	}

	private BMSModel decode(ChartSource source, byte[] data, boolean ispms, int[] selectedRandom) {
		final String path = source != null ? source.location() : null;
		final boolean legacyFreeZoneEnabled = !ispms && source != null
				&& source.lastModifiedTimeMillis() > 0
				&& source.lastModifiedTimeMillis() <= LEGACY_FREEZONE_CUTOFF_MILLIS;
		log.clear();
		final long time = System.currentTimeMillis();
		BMSModel model = new BMSModel();
		scrolltable.clear();
		speedtable.clear();
		stoptable.clear();
		bpmtable.clear();

		MessageDigest md5digest, sha256digest;
		try {
			md5digest = MessageDigest.getInstance("MD5");
			sha256digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e1) {
			e1.printStackTrace();
			return null;
		}

		int maxsec = 0;
		// BMS読み込み、ハッシュ値取得
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new DigestInputStream(new DigestInputStream(new ByteArrayInputStream(data), md5digest), sha256digest),
				"MS932"));) {
			model.setMode(ispms ? Mode.POPN_9K : Mode.BEAT_5K);
			// Logger.getGlobal().info(
			// "BMSデータ読み込み時間(ms) :" + (System.currentTimeMillis() - time));

			String line = null;
			wavlist.clear();
			Arrays.fill(wm, -2);
			bgalist.clear();
			Arrays.fill(bm, -2);
			for (List<String> l : lines) {
				if (l != null) {
					l.clear();
				}
			}

			randoms.clear();
			srandoms.clear();
			crandom.clear();

			skip.clear();
			final List<ConditionalBlock> conditionalBlocks = new ArrayList<ConditionalBlock>();
			final List<ConditionalBlock.ChannelLineEntry> channelEntries = new ArrayList<ConditionalBlock.ChannelLineEntry>();
			ConditionalParseState condState = null;
			while ((line = br.readLine()) != null) {
				if (line.length() < 2) {
					continue;
				}

				if(line.charAt(0) == '#') {
					// line = line.substring(1, line.length());
					// RANDOM制御系
					if (matchesReserveWord(line, "RANDOM")) {
						try {
							final int r = Integer.parseInt(line.substring(8).trim());
							randoms.add(r);
							if (selectedRandom != null) {
								crandom.add(selectedRandom[randoms.size() - 1]);
							} else {
								crandom.add((int) (Math.random() * r) + 1);
								srandoms.add(crandom.getLast());
							}
						} catch (NumberFormatException e) {
							log.add(new DecodeLog(WARNING, "#RANDOMに数字が定義されていません"));
						}
					} else if (matchesReserveWord(line, "IF")) {
						// RANDOM分岐開始
						if(!crandom.isEmpty()) {
							try {
								skip.add((crandom.getLast() != Integer.parseInt(line.substring(4).trim())));
							} catch (NumberFormatException e) {
								log.add(new DecodeLog(WARNING, "#IFに数字が定義されていません"));
							}
						} else {
							log.add(new DecodeLog(WARNING, "#IFに対応する#RANDOMが定義されていません"));
						}
					} else if (matchesReserveWord(line, "ENDIF")) {
						if (!skip.isEmpty()) {
							skip.removeLast();
						} else {
							log.add(new DecodeLog(WARNING, "ENDIFに対応するIFが存在しません: " + line));
						}
					} else if (matchesReserveWord(line, "ENDRANDOM")) {
						if (!crandom.isEmpty()) {
							crandom.removeLast();
						} else {
							log.add(new DecodeLog(WARNING, "ENDRANDOMに対応するRANDOMが存在しません: " + line));
						}
					} else if (skip.isEmpty() || !skip.getLast()) {
						if (matchesConditionalKeyword(line, "CONDITIONAL")) {
							condState = handleConditionalStart(line, condState, conditionalBlocks.size());
							continue;
						} else if (matchesConditionalKeyword(line, "WHEN")) {
							handleConditionalWhen(line, condState, false);
							continue;
						} else if (matchesConditionalKeyword(line, "ELSEWHEN")) {
							handleConditionalWhen(line, condState, true);
							continue;
						} else if (matchesConditionalKeyword(line, "DEFAULT")) {
							handleConditionalDefault(line, condState);
							continue;
						} else if (matchesConditionalKeyword(line, "ENDCONDITIONAL")) {
							maxsec = handleConditionalEnd(line, condState, conditionalBlocks, channelEntries, lines, maxsec);
							condState = null;
							continue;
						} else if (condState != null) {
							handleLineInsideConditional(line, condState);
							continue;
						}
						final char c = line.charAt(1);
						final int base = model.getBase();
						if ('0' <= c && c <= '9' && line.length() > 6) {
							// line = line.toUpperCase();
							// 楽譜
							final char c2 = line.charAt(2);
							final char c3 = line.charAt(3);
							if ('0' <= c2 && c2 <= '9' && '0' <= c3 && c3 <= '9') {
								final int bar_index = (c - '0') * 100 + (c2 - '0') * 10 + (c3 - '0');
								List<String> l = lines[bar_index];
								if (l == null) {
									l = lines[bar_index] = new ArrayList<String>();
								}
								l.add(line);
								maxsec = (maxsec > bar_index) ? maxsec : bar_index;
								channelEntries.add(new ConditionalBlock.ChannelLineEntry(-1, -1, bar_index, line));
							} else {
								log.add(new DecodeLog(WARNING, "小節に数字が定義されていません : " + line));
							}
						} else if (matchesReserveWord(line, "BPM")) {
							if (line.charAt(4) == ' ') {
								// BPMは小数点のケースがある(FREEDOM DiVE)
								try {
									final String arg = line.substring(5).trim();
									double bpm = Double.parseDouble(arg);
									if(bpm > 0) {
										model.setBpm(bpm);
									} else {
										log.add(new DecodeLog(WARNING, "#negative BPMはサポートされていません : " + line));
									}
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#BPMに数字が定義されていません : " + line));
								}
							} else {
								try {
									double bpm = Double.parseDouble(line.substring(7).trim());
									if(bpm > 0) {
										if(base == 62) {
											bpmtable.put(ChartDecoder.parseInt62(line, 4), bpm);
										} else {
											bpmtable.put(ChartDecoder.parseInt36(line, 4), bpm);
										}
									} else {
										log.add(new DecodeLog(WARNING, "#negative BPMはサポートされていません : " + line));
									}
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#BPMxxに数字が定義されていません : " + line));
								}
							}
						} else if (matchesReserveWord(line, "WAV")) {
							// 音源ファイル
							if (line.length() >= 8) {
								try {
									final String file_name = line.substring(7).trim().replace('\\', '/');
									if(base == 62) {
										wm[ChartDecoder.parseInt62(line, 4)] = wavlist.size();
									} else {
										wm[ChartDecoder.parseInt36(line, 4)] = wavlist.size();
									}
									wavlist.add(file_name);
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#WAVxxは不十分な定義です : " + line));
								}
							} else {
								log.add(new DecodeLog(WARNING, "#WAVxxは不十分な定義です : " + line));
							}
						} else if (matchesReserveWord(line, "BMP")) {
							// BGAファイル
							if (line.length() >= 8) {
								try {
									final String file_name = line.substring(7).trim().replace('\\', '/');
									if(base == 62) {
										bm[ChartDecoder.parseInt62(line, 4)] = bgalist.size();
									} else {
										bm[ChartDecoder.parseInt36(line, 4)] = bgalist.size();
									}
									bgalist.add(file_name);
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#BMPxxは不十分な定義です : " + line));
								}
							} else {
								log.add(new DecodeLog(WARNING, "#BMPxxは不十分な定義です : " + line));
							}
						} else if (matchesReserveWord(line, "STOP")) {
							if (line.length() >= 9) {
								try {
									double stop = Double.parseDouble(line.substring(8).trim()) / 192;
									if(stop < 0) {
										stop = Math.abs(stop);
										log.add(new DecodeLog(WARNING, "#negative STOPはサポートされていません : " + line));
									}
									if(base == 62) {
										stoptable.put(ChartDecoder.parseInt62(line, 5), stop);
									} else {
										stoptable.put(ChartDecoder.parseInt36(line, 5), stop);
									}
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#STOPxxに数字が定義されていません : " + line));
								}
							} else {
								log.add(new DecodeLog(WARNING, "#STOPxxは不十分な定義です : " + line));
							}
						} else if (matchesReserveWord(line, "SCROLL")) {
							if (line.length() >= 11) {
								try {
									double scroll = Double.parseDouble(line.substring(10).trim());
									if(base == 62) {
										scrolltable.put(ChartDecoder.parseInt62(line, 7), scroll);
									} else {
										scrolltable.put(ChartDecoder.parseInt36(line, 7), scroll);
									}
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#SCROLLxxに数字が定義されていません : " + line));
								}
							} else {
								log.add(new DecodeLog(WARNING, "#SCROLLxxは不十分な定義です : " + line));
							}
						} else if (matchesReserveWord(line, "SPEED")) {
							if (line.length() >= 10) {
								try {
									double speed = Double.parseDouble(line.substring(9).trim());
									if(base == 62) {
										speedtable.put(ChartDecoder.parseInt62(line, 6), speed);
									} else {
										speedtable.put(ChartDecoder.parseInt36(line, 6), speed);
									}
								} catch (NumberFormatException e) {
									log.add(new DecodeLog(WARNING, "#SPEEDxxに数字が定義されていません : " + line));
								}
							} else {
								log.add(new DecodeLog(WARNING, "#SPEEDxxは不十分な定義です : " + line));
							}
						} else {
							for (CommandWord cw : commandWords) {
								if (matchesReserveWord(line, cw.name()) && (line.length() > cw.name().length() + 2
										|| ((cw == CommandWord.LNOBJ || cw == CommandWord.CNOBJ || cw == CommandWord.HCNOBJ || cw == CommandWord.HLNOBJ)
												&& (line.length() == cw.name().length() + 1 || Character.isWhitespace(line.charAt(cw.name().length() + 1)))))) {
									final String rawArg = line.length() > cw.name().length() + 2 ? line.substring(cw.name().length() + 2).trim() : "";
									DecodeLog log = cw.function.apply(model, rawArg);
									if (log != null) {
										this.log.add(log);
										Logger.getGlobal().warning(model.getTitle() + " - " + log.getMessage() + " : " + line);
									}
									break;
								}
							}
						}
					}
				} else if(line.charAt(0) == '%') {
					final int index = line.indexOf(' ');
					if(index > 0 && line.length() > index + 1) {
						model.getValues().put(line.substring(1, index), line.substring(index + 1));
					}
				} else if(line.charAt(0) == '@') {
					final int index = line.indexOf(' ');
					if(index > 0 && line.length() > index + 1) {
						model.getValues().put(line.substring(1, index), line.substring(index + 1));
					}
				}
			}
			
			if (condState != null) {
				log.add(new DecodeLog(ERROR, "#CONDITIONALに対応する#ENDCONDITIONALが存在しません"));
			}
			model.setWavList(wavlist.toArray(new String[wavlist.size()]));
			model.setBgaList(bgalist.toArray(new String[bgalist.size()]));

			Section prev = null;
			Section[] sections = new Section[maxsec + 1];
			for (int i = 0; i <= maxsec; i++) {
				sections[i] = new Section(model, prev, lines[i] != null ? lines[i] : Collections.EMPTY_LIST, bpmtable,
						stoptable, scrolltable, speedtable, log, legacyFreeZoneEnabled);
				prev = sections[i];
			}

			final TreeMap<Double, TimeLineCache> timelines = new TreeMap<Double, TimeLineCache>();
			final List<LongNote>[] lnlist = new List[model.getMode().key];
			LongNote[] lnendstatus = new LongNote[model.getMode().key];
			final TimeLine basetl = new TimeLine(0, 0, model.getMode().key);
			basetl.setBPM(model.getBpm());
			if (bm[0] != -2) {
				// #BMP00が定義されている場合、デフォルトのミスレイヤーとして設定する
				final Layer.Sequence[] poors = new Layer.Sequence[] {
						new Layer.Sequence(0, bm[0]),
						new Layer.Sequence(500)
				};
				basetl.setEventlayer(new Layer[] {new Layer(new Layer.Event(Layer.EventType.MISS, 1), new Layer.Sequence[][] {poors})});
			}
			timelines.put(0.0, new TimeLineCache(0.0, basetl));
			for (Section section : sections) {
				section.makeTimeLines(wm, bm, timelines, lnlist, lnendstatus);
			}
			// Logger.getGlobal().info(
			// "Section生成時間(ms) :" + (System.currentTimeMillis() - time));
			Section.resolveFreeZones(sections, model, timelines, log);
			TimeLine[] tl = new TimeLine[timelines.size()];
			int tlcount = 0;
			for(TimeLineCache tlc : timelines.values()) {
				tl[tlcount] = tlc.timeline;
				tlcount++;
			}
			model.setAllTimeLine(tl);

			if(tl[0].getBPM() == 0) {
				log.add(new DecodeLog(ERROR, "開始BPMが定義されていないため、BMS解析に失敗しました"));
				Logger.getGlobal().severe(path + ":BMSファイル解析失敗: 開始BPMが定義されていません");
				return null;
			}

			for (int i = 0; i < lnendstatus.length; i++) {
				if (lnendstatus[i] != null) {
					log.add(new DecodeLog(WARNING, "曲の終端までにLN終端定義されていないLNがあります。lane:" + (i + 1)));
					if(lnendstatus[i].getSection() != Double.MIN_VALUE) {
						timelines.get(lnendstatus[i].getSection()).timeline.setNote(i,  null);
					}
				}
			}

			if (!conditionalBlocks.isEmpty()) {
				prepareConditionalModel(model, conditionalBlocks, channelEntries, bpmtable, stoptable, scrolltable, speedtable, wm, bm, maxsec, legacyFreeZoneEnabled);
				tl = model.getAllTimeLines();
				if (tl.length == 0 || tl[0].getBPM() == 0) {
					log.add(new DecodeLog(ERROR, "開始BPMが定義されていないため、BMS解析に失敗しました"));
					Logger.getGlobal().severe(path + ":BMSファイル解析失敗: 開始BPMが定義されていません");
					return null;
				}
			}

			if (model.getTotalType() != BMSModel.TotalType.BMS) {
				log.add(new DecodeLog(WARNING, "TOTALが未定義です"));
			}
			if (model.getTotal() <= 60.0) {
				log.add(new DecodeLog(WARNING, "TOTAL値が少なすぎます"));
			}
			if (tl.length > 0) {
				if (tl[tl.length - 1].getTime() >= model.getLastTime() + 30000) {
					log.add(new DecodeLog(WARNING, "最後のノート定義から30秒以上の余白があります"));
				}
			}
			if (model.getPlayer() > 1 && (model.getMode() == Mode.BEAT_5K || model.getMode() == Mode.BEAT_7K)) {
				log.add(new DecodeLog(WARNING, "#PLAYER定義が2以上にもかかわらず2P側のノーツ定義が一切ありません"));
			}
			if (model.getPlayer() == 1 && (model.getMode() == Mode.BEAT_10K || model.getMode() == Mode.BEAT_14K)) {
				log.add(new DecodeLog(WARNING, "#PLAYER定義が1にもかかわらず2P側のノーツ定義が存在します"));
			}
			model.setMD5(convertHexString(md5digest.digest()));
			model.setSHA256(convertHexString(sha256digest.digest()));
			log.add(new DecodeLog(INFO, "#PLAYER定義が1にもかかわらず2P側のノーツ定義が存在します"));
			Logger.getGlobal().fine("BMSデータ解析時間(ms) :" + (System.currentTimeMillis() - time));

			if (selectedRandom == null) {
				selectedRandom = new int[srandoms.size()];
				final Iterator<Integer> ri = srandoms.iterator();
				for (int i = 0; i < selectedRandom.length; i++) {
					selectedRandom[i] = ri.next();
				}
			}
			
			model.setChartInformation(new ChartInformation(source, lntype, selectedRandom));
			printLog(path);
			return model;
		} catch (IOException e) {
			log.add(new DecodeLog(ERROR, "BMSファイルへのアクセスに失敗しました"));
			Logger.getGlobal()
					.severe(path + ":BMSファイル解析失敗: " + e.getClass().getName() + " - " + e.getMessage());
		} catch (Exception e) {
			log.add(new DecodeLog(ERROR, "何らかの異常によりBMS解析に失敗しました"));
			Logger.getGlobal()
					.severe(path + ":BMSファイル解析失敗: " + e.getClass().getName() + " - " + e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private boolean matchesConditionalKeyword(String line, String keyword) {
		if (!matchesReserveWord(line, keyword)) {
			return false;
		}
		final int nextIdx = keyword.length() + 1;
		return line.length() == nextIdx || Character.isWhitespace(line.charAt(nextIdx));
	}

	private static class ConditionalParseState {
		final int blockIndex;
		final int evalBar;
		final double evalFraction;
		final boolean invalid;
		final List<ConditionalBlock.Branch> branches = new ArrayList<ConditionalBlock.Branch>();
		final List<ConditionalBlock.ChannelLineEntry> pendingEntries = new ArrayList<ConditionalBlock.ChannelLineEntry>();
		boolean hasDefault = false;
		int currentBranchIndex = -1;
		boolean skipCurrentBranch = false;

		ConditionalParseState(int blockIndex, int evalBar, double evalFraction, boolean invalid) {
			this.blockIndex = blockIndex;
			this.evalBar = evalBar;
			this.evalFraction = evalFraction;
			this.invalid = invalid;
		}
	}

	private ConditionalParseState handleConditionalStart(String line, ConditionalParseState current, int nextBlockIndex) {
		if (current != null) {
			log.add(new DecodeLog(ERROR, "#CONDITIONALの入れ子はサポートされていません : " + line));
			return new ConditionalParseState(-1, 0, 0.0, true);
		}
		final String arg = line.length() > 12 ? line.substring(12).trim() : "";
		if (arg.isEmpty()) {
			log.add(new DecodeLog(ERROR, "#CONDITIONALの評価位置が定義されていません : " + line));
			return new ConditionalParseState(-1, 0, 0.0, true);
		}
		try {
			final double evalPos = Double.parseDouble(arg);
			if (Double.isNaN(evalPos) || Double.isInfinite(evalPos) || evalPos < 0.0 || evalPos >= 1000.0) {
				log.add(new DecodeLog(ERROR, "#CONDITIONALの評価位置が範囲外です : " + line));
				return new ConditionalParseState(-1, 0, 0.0, true);
			}
			int evalBar = (int) Math.floor(evalPos);
			double evalFraction = evalPos - evalBar;
			if (evalFraction < 1e-9) {
				evalFraction = 0.0;
			} else if (evalFraction >= 1.0 - 1e-9) {
				evalBar++;
				evalFraction = 0.0;
			}
			if (evalBar >= 1000) {
				log.add(new DecodeLog(ERROR, "#CONDITIONALの評価位置が範囲外です : " + line));
				return new ConditionalParseState(-1, 0, 0.0, true);
			}
			return new ConditionalParseState(nextBlockIndex, evalBar, evalFraction, false);
		} catch (NumberFormatException e) {
			log.add(new DecodeLog(ERROR, "#CONDITIONALの評価位置が不正です : " + line));
			return new ConditionalParseState(-1, 0, 0.0, true);
		}
	}

	private void handleConditionalWhen(String line, ConditionalParseState state, boolean isElseWhen) {
		final String cmd = isElseWhen ? "#ELSEWHEN" : "#WHEN";
		if (state == null) {
			log.add(new DecodeLog(ERROR, cmd + "に対応する#CONDITIONALが存在しません : " + line));
			return;
		}
		if (state.invalid) {
			return;
		}
		if (!isElseWhen && !state.branches.isEmpty()) {
			log.add(new DecodeLog(ERROR, "#WHENはブロック先頭に1つのみ記述可能です : " + line));
			state.skipCurrentBranch = true;
			return;
		}
		if (isElseWhen && (state.branches.isEmpty() || state.hasDefault)) {
			log.add(new DecodeLog(ERROR, "#ELSEWHENの位置が不正です : " + line));
			state.skipCurrentBranch = true;
			return;
		}
		final int prefixLen = isElseWhen ? 9 : 5;
		final String expr = line.length() > prefixLen ? line.substring(prefixLen).trim() : "";
		final ConditionalBlock.Condition cond = ConditionalBlock.Condition.parse(expr);
		if (cond == null) {
			log.add(new DecodeLog(ERROR, cmd + "の条件式が不正です : " + line));
			state.skipCurrentBranch = true;
			return;
		}
		state.branches.add(new ConditionalBlock.Branch(cond));
		state.currentBranchIndex = state.branches.size() - 1;
		state.skipCurrentBranch = false;
	}

	private void handleConditionalDefault(String line, ConditionalParseState state) {
		if (state == null) {
			log.add(new DecodeLog(ERROR, "#DEFAULTに対応する#CONDITIONALが存在しません : " + line));
			return;
		}
		if (state.invalid) {
			return;
		}
		if (state.branches.isEmpty() || state.hasDefault) {
			log.add(new DecodeLog(ERROR, "#DEFAULTの位置が不正です : " + line));
			state.skipCurrentBranch = true;
			return;
		}
		state.branches.add(new ConditionalBlock.Branch(null));
		state.currentBranchIndex = state.branches.size() - 1;
		state.hasDefault = true;
		state.skipCurrentBranch = false;
	}

	private int handleConditionalEnd(String line, ConditionalParseState state,
			List<ConditionalBlock> conditionalBlocks,
			List<ConditionalBlock.ChannelLineEntry> channelEntries,
			List<String>[] barLines, int maxsec) {
		if (state == null) {
			log.add(new DecodeLog(ERROR, "#ENDCONDITIONALに対応する#CONDITIONALが存在しません : " + line));
			return maxsec;
		}
		if (state.invalid) {
			return maxsec;
		}
		if (!state.hasDefault) {
			log.add(new DecodeLog(ERROR, "#CONDITIONALブロックに#DEFAULTが存在しません : " + line));
			return maxsec;
		}
		final ConditionalBlock block = new ConditionalBlock(state.blockIndex, state.evalBar, state.evalFraction, state.branches);
		conditionalBlocks.add(block);
		final int defaultBranchIdx = block.getDefaultBranchIndex();
		for (ConditionalBlock.ChannelLineEntry entry : state.pendingEntries) {
			channelEntries.add(entry);
			if (entry.branchIndex == defaultBranchIdx) {
				List<String> l = barLines[entry.barIndex];
				if (l == null) {
					l = barLines[entry.barIndex] = new ArrayList<String>();
				}
				l.add(entry.line);
			}
			if (entry.barIndex > maxsec) {
				maxsec = entry.barIndex;
			}
		}
		if (block.getEvalBar() > maxsec) {
			maxsec = block.getEvalBar();
		}
		return maxsec;
	}

	private void handleLineInsideConditional(String line, ConditionalParseState state) {
		if (state.invalid) {
			return;
		}
		final char c = line.charAt(1);
		if ('0' <= c && c <= '9' && line.length() > 6) {
			if (state.currentBranchIndex < 0 || state.skipCurrentBranch) {
				return;
			}
			final char c2 = line.charAt(2);
			final char c3 = line.charAt(3);
			if ('0' <= c2 && c2 <= '9' && '0' <= c3 && c3 <= '9') {
				final int barIndex = (c - '0') * 100 + (c2 - '0') * 10 + (c3 - '0');
				final String sanitized = sanitizeBranchChannelLine(line, barIndex, state.evalBar, state.evalFraction);
				if (sanitized != null) {
					state.pendingEntries.add(new ConditionalBlock.ChannelLineEntry(
							state.blockIndex, state.currentBranchIndex, barIndex, sanitized));
				}
			} else {
				log.add(new DecodeLog(WARNING, "小節に数字が定義されていません : " + line));
			}
		} else {
			log.add(new DecodeLog(WARNING, "#CONDITIONALブロック内のヘッダ定義は無視されます : " + line));
		}
	}

	private String sanitizeBranchChannelLine(String line, int barIndex, int evalBar, double evalFraction) {
		final int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
		if (channel == Section.SECTION_RATE) {
			final boolean allowed = (evalFraction == 0.0) ? (barIndex >= evalBar) : (barIndex > evalBar);
			if (!allowed) {
				log.add(new DecodeLog(WARNING, "#CONDITIONALの評価位置以前の小節長変更は無視されます : " + line));
				return null;
			}
			return line;
		}
		int findex = 6;
		final int len = line.length();
		boolean foundSep = false;
		while (findex < len) {
			final char ch = line.charAt(findex);
			if (ch == ':' || Character.isWhitespace(ch)) {
				foundSep = true;
				findex++;
			} else {
				break;
			}
		}
		if (!foundSep) {
			return line;
		}
		int lindex = len;
		while (lindex > findex && Character.isWhitespace(line.charAt(lindex - 1))) {
			lindex--;
		}
		final int split = (lindex - findex) / 2;
		if (split <= 0) {
			return null;
		}
		if (barIndex > evalBar) {
			return line;
		}
		if (barIndex < evalBar) {
			for (int i = 0; i < split; i++) {
				if (line.charAt(findex + i * 2) != '0' || line.charAt(findex + i * 2 + 1) != '0') {
					log.add(new DecodeLog(WARNING, "#CONDITIONALの評価位置より前のオブジェクト定義は無視されます : " + line));
					break;
				}
			}
			return null;
		}
		// barIndex == evalBar
		if (evalFraction <= 0.0) {
			return line;
		}
		boolean hasPastNonZero = false;
		boolean hasFutureNonZero = false;
		for (int i = 0; i < split; i++) {
			final boolean nonZero = (line.charAt(findex + i * 2) != '0' || line.charAt(findex + i * 2 + 1) != '0');
			if (nonZero) {
				final double pos = (double) i / split;
				if (pos < evalFraction - 1e-9) {
					hasPastNonZero = true;
				} else {
					hasFutureNonZero = true;
				}
			}
		}
		if (!hasPastNonZero) {
			return line;
		}
		log.add(new DecodeLog(WARNING, "#CONDITIONALの評価位置より前のオブジェクト定義は無視されます : " + line));
		if (!hasFutureNonZero) {
			return null;
		}
		final StringBuilder sb = new StringBuilder(line.length());
		sb.append(line, 0, findex);
		for (int i = 0; i < split; i++) {
			final double pos = (double) i / split;
			if (pos < evalFraction - 1e-9) {
				sb.append("00");
			} else {
				sb.append(line.charAt(findex + i * 2));
				sb.append(line.charAt(findex + i * 2 + 1));
			}
		}
		return sb.toString();
	}

	private void prepareConditionalModel(BMSModel model,
			List<ConditionalBlock> conditionalBlocks,
			List<ConditionalBlock.ChannelLineEntry> channelEntries,
			Map<Integer, Double> bpmtable, Map<Integer, Double> stoptable,
			Map<Integer, Double> scrolltable, Map<Integer, Double> speedtable,
			int[] wm, int[] bm, int maxsec, boolean legacyFreeZoneEnabled) {
		final int base = model.getBase();
		final int lnobj = model.getLnobj();
		final int cnobj = model.getCnobj();
		final int hcnobj = model.getHcnobj();
		final int hlnobj = model.getHlnobj();
		final Set<Integer> wavIdSet = new TreeSet<Integer>();
		boolean hasMine = false;
		boolean hasFreeZone = false;
		int maxBar = maxsec;

		for (ConditionalBlock.ChannelLineEntry entry : channelEntries) {
			if (entry.barIndex > maxBar) {
				maxBar = entry.barIndex;
			}
			final String line = entry.line;
			final int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
			int basech = 0;
			int ch2 = -1;
			for (int ch : Section.NOTE_CHANNELS) {
				if (ch <= channel && channel <= ch + 8) {
					basech = ch;
					ch2 = channel - ch;
					break;
				}
			}
			int findex = 6;
			final int len = line.length();
			boolean foundSep = false;
			while (findex < len) {
				final char ch = line.charAt(findex);
				if (ch == ':' || Character.isWhitespace(ch)) {
					foundSep = true;
					findex++;
				} else {
					break;
				}
			}
			if (!foundSep) {
				continue;
			}
			int lindex = len;
			while (lindex > findex && Character.isWhitespace(line.charAt(lindex - 1))) {
				lindex--;
			}
			final int split = (lindex - findex) / 2;
			boolean hasNonZero = false;
			for (int i = 0; i < split; i++) {
				final char c1 = line.charAt(findex + i * 2);
				final char c2 = line.charAt(findex + i * 2 + 1);
				final int data = (base == 62) ? ChartDecoder.parseInt62(c1, c2) : ChartDecoder.parseInt36(c1, c2);
				if (data > 0) {
					hasNonZero = true;
					if (entry.blockIndex >= 0) {
						if (channel == Section.LANE_AUTOPLAY
								|| basech == Section.P1_KEY_BASE || basech == Section.P2_KEY_BASE
								|| basech == Section.P1_INVISIBLE_KEY_BASE || basech == Section.P2_INVISIBLE_KEY_BASE
								|| basech == Section.P1_LONG_KEY_BASE || basech == Section.P2_LONG_KEY_BASE) {
							final boolean isMarker = (basech == Section.P1_KEY_BASE || basech == Section.P2_KEY_BASE)
									&& (data == lnobj || data == cnobj || data == hcnobj || data == hlnobj);
							if (!isMarker && data < wm.length && wm[data] >= 0) {
								wavIdSet.add(wm[data]);
							}
						} else if (basech == Section.P1_MINE_KEY_BASE || basech == Section.P2_MINE_KEY_BASE) {
							hasMine = true;
							if (wm[0] >= 0) {
								wavIdSet.add(wm[0]);
							}
						} else if (model.getMode() != Mode.POPN_9K && (channel == Section.P1_FREEZONE_SINGLE
								|| channel == Section.P1_FREEZONE_PAIR
								|| channel == Section.P2_FREEZONE_SINGLE
								|| channel == Section.P2_FREEZONE_PAIR
								|| (legacyFreeZoneEnabled && (channel == Section.P1_FREEZONE_LEGACY || channel == Section.P2_FREEZONE_LEGACY)))) {
							hasFreeZone = true;
						}
						if (model.getMode() != Mode.POPN_9K && legacyFreeZoneEnabled
								&& (channel == Section.P1_FREEZONE_LEGACY || channel == Section.P2_FREEZONE_LEGACY)) {
							hasFreeZone = true;
						}
					}
				}
			}
			if (hasNonZero) {
				if (ch2 == 7 || ch2 == 8) {
					final Mode nextMode = (model.getMode() == Mode.BEAT_5K) ? Mode.BEAT_7K
							: (model.getMode() == Mode.BEAT_10K ? Mode.BEAT_14K : null);
					if (nextMode != null) {
						model.setMode(nextMode);
					}
				}
				if (((basech == Section.P2_KEY_BASE || basech == Section.P2_INVISIBLE_KEY_BASE
						|| basech == Section.P2_LONG_KEY_BASE || basech == Section.P2_MINE_KEY_BASE)
						&& (channel != Section.P2_FREEZONE_LEGACY || legacyFreeZoneEnabled))
						|| (model.getMode() != Mode.POPN_9K && (channel == Section.P2_FREEZONE_SINGLE || channel == Section.P2_FREEZONE_PAIR))) {
					final Mode nextMode = (model.getMode() == Mode.BEAT_5K) ? Mode.BEAT_10K
							: (model.getMode() == Mode.BEAT_7K ? Mode.BEAT_14K : null);
					if (nextMode != null) {
						model.setMode(nextMode);
					}
				}
			}
		}

		final int[] condWavIds = new int[wavIdSet.size()];
		int idx = 0;
		for (int wid : wavIdSet) {
			condWavIds[idx++] = wid;
		}
		final ConditionalBlock[] blocksArray = conditionalBlocks.toArray(new ConditionalBlock[0]);
		final ConditionalBlock.ChartBuilder builder = new ConditionalBlock.ChartBuilder(
				channelEntries, blocksArray, bpmtable, stoptable, scrolltable, speedtable,
				wm, bm, model.getMode(), maxBar, legacyFreeZoneEnabled);

		model.setConditionalBlocks(blocksArray);
		model.setConditionalChartBuilder(builder);
		model.setConditionalWavIds(condWavIds);
		model.setConditionalContainsMineNote(hasMine);
		model.setConditionalContainsFreeZone(hasFreeZone);

		builder.buildTimeLines(model, null, log);
	}

	private boolean matchesReserveWord(String line, String s) {
		final int len = s.length();
		if (line.length() <= len) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			final char c = line.charAt(i + 1);
			final char c2 = s.charAt(i);
			if (c != c2 && c != c2 + 32) {
				return false;
			}
		}
		return true;
	}

	/**
	 * バイトデータを16進数文字列表現に変換する
	 * 
	 * @param data
	 *            バイトデータ
	 * @returnバイトデータの16進数文字列表現
	 */
	public static String convertHexString(byte[] data) {
		final StringBuilder sb = new StringBuilder(data.length * 2);
		for (byte b : data) {
			sb.append(Character.forDigit(b >> 4 & 0xf, 16));
			sb.append(Character.forDigit(b & 0xf, 16));
		}
		return sb.toString();
	}
}

/**
 * 予約語
 *
 * @author exch
 */
enum CommandWord {

	PLAYER ((model, arg) -> {
		try {
			final int player = Integer.parseInt(arg);
			// TODO playerの許容幅は？
			if (player >= 1 && player < 3) {
				model.setPlayer(player);
			} else {
				return new DecodeLog(WARNING, "#PLAYERに規定外の数字が定義されています : " + player);
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#PLAYERに数字が定義されていません");
		}
		return null;
	}),
	GENRE ((model, arg) -> {
		model.setGenre(arg);
		return null;
	}),
	TITLE ((model, arg) -> {
		model.setTitle(arg);
		return null;
	}),
	SUBTITLE ((model, arg) -> {
		model.setSubTitle(arg);
		return null;
	}),
	ARTIST ((model, arg) -> {
		model.setArtist(arg);
		return null;
	}),
	SUBARTIST ((model, arg) -> {
		model.setSubArtist(arg);
		return null;
	}),
	PLAYLEVEL ((model, arg) -> {
		model.setPlaylevel(arg);
		return null;
	}),
	RANK ((model, arg) -> {
		try {
			final int rank = Integer.parseInt(arg);
			if (rank >= 0 && rank < 5) {
				model.setJudgerank(rank);
				model.setJudgerankType(BMSModel.JudgeRankType.BMS_RANK);
			} else {
				return new DecodeLog(WARNING, "#RANKに規定外の数字が定義されています : " + rank);
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#RANKに数字が定義されていません");
		}
		return null;
	}),
	DEFEXRANK ((model, arg) -> {
		try {
			final int rank = Integer.parseInt(arg);
			if (rank >= 1) {
				model.setJudgerank(rank);
				model.setJudgerankType(BMSModel.JudgeRankType.BMS_DEFEXRANK);
			} else {
				return new DecodeLog(WARNING, "#DEFEXRANK 1以下はサポートしていません" + rank);
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#DEFEXRANKに数字が定義されていません");
		}
		return null;
	}),
	TOTAL ((model, arg) -> {
		try {
			final double total = Double.parseDouble(arg);
			if(total > 0) {
				model.setTotal(total);
				model.setTotalType(BMSModel.TotalType.BMS);
			} else {
				return new DecodeLog(WARNING, "#TOTALが0以下です");
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#TOTALに数字が定義されていません");
		}
		return null;
	}),
	VOLWAV ((model, arg) -> {
		try {
			model.setVolwav(Integer.parseInt(arg));
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#VOLWAVに数字が定義されていません");
		}
		return null;
	}),
	STAGEFILE ((model, arg) -> {
		model.setStagefile(arg.replace('\\', '/'));
		return null;
	}),
	BACKBMP ((model, arg) -> {
		model.setBackbmp(arg.replace('\\', '/'));
		return null;
	}),
	PREVIEW ((model, arg) -> {
		model.setPreview(arg.replace('\\', '/'));
		return null;
	}),
	LNOBJ ((model, arg) -> {
		try {
			if (model.getBase() == 62) {
				int id = parseObjId(model, arg);
				if (id >= 1) {
					model.setLnobj(id);
				} else {
					return new DecodeLog(WARNING, "#LNOBJに無効なIDが定義されています");
				}
			} else {
				int id = parseObjId(model, arg);
				if (id >= 1) {
					model.setLnobj(id);
				} else {
					return new DecodeLog(WARNING, "#LNOBJに無効なIDが定義されています");
				}
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#LNOBJに数字が定義されていません");
		}
		return null;
	}),
	CNOBJ ((model, arg) -> {
		try {
			int id = parseObjId(model, arg);
			if (id >= 1) {
				model.setCnobj(id);
			} else {
				return new DecodeLog(WARNING, "#CNOBJに無効なIDが定義されています");
			}
		} catch (NumberFormatException | IndexOutOfBoundsException e) {
			return new DecodeLog(WARNING, "#CNOBJに数字が定義されていません");
		}
		return null;
	}),
	HCNOBJ ((model, arg) -> {
		try {
			int id = parseObjId(model, arg);
			if (id >= 1) {
				model.setHcnobj(id);
			} else {
				return new DecodeLog(WARNING, "#HCNOBJに無効なIDが定義されています");
			}
		} catch (NumberFormatException | IndexOutOfBoundsException e) {
			return new DecodeLog(WARNING, "#HCNOBJに数字が定義されていません");
		}
		return null;
	}),
	HLNOBJ ((model, arg) -> {
		try {
			int id = parseObjId(model, arg);
			if (id >= 1) {
				model.setHlnobj(id);
			} else {
				return new DecodeLog(WARNING, "#HLNOBJに無効なIDが定義されています");
			}
		} catch (NumberFormatException | IndexOutOfBoundsException e) {
			return new DecodeLog(WARNING, "#HLNOBJに数字が定義されていません");
		}
		return null;
	}),
	LNMODE ((model, arg) -> {
		try {
			int lnmode = Integer.parseInt(arg);
			if(lnmode < 0 || lnmode > 4) {
				return new DecodeLog(WARNING, "#LNMODEに無効な数字が定義されています");
			}

			// LR2oraja Endless Dream: LR2 does not support LNMODE, suppress modes 1 or 2
			// lnmode = 0;

			if (lnmode >= 1) {
				model.setLnmode(lnmode);
			}
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#LNMODEに数字が定義されていません");
		}
		return null;
	}),
	DIFFICULTY ((model, arg) -> {
		try {
			model.setDifficulty(Integer.parseInt(arg));
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#DIFFICULTYに数字が定義されていません");
		}
		return null;
	}),
	BANNER ((model, arg) -> {
		model.setBanner(arg.replace('\\', '/'));
		return null;
	}),
	COMMENT ((model, arg) -> {
		// TODO 未実装
		return null;
	}),
	BASE ((model, arg) -> {
		try {
			int base = Integer.parseInt(arg);
			if(base != 62) {
				return new DecodeLog(WARNING, "#BASEに無効な数字が定義されています");
			}
			model.setBase(base);
		} catch (NumberFormatException e) {
			return new DecodeLog(WARNING, "#BASEに数字が定義されていません");
		}
		return null;
	});

	public final BiFunction<BMSModel, String, DecodeLog> function;

	private static int parseObjId(BMSModel model, String arg) {
		final String trimmed = arg.trim();
		if (trimmed.isEmpty()) {
			throw new NumberFormatException();
		}
		int spaceIdx = 0;
		while (spaceIdx < trimmed.length() && !Character.isWhitespace(trimmed.charAt(spaceIdx))) {
			spaceIdx++;
		}
		arg = trimmed.substring(0, spaceIdx);
		if (arg.isEmpty() || arg.length() > 2) {
			throw new NumberFormatException();
		}
		if (model.getBase() == 62) {
			if (arg.length() != 2) {
				throw new NumberFormatException();
			}
			return ChartDecoder.parseInt62(arg, 0);
		} else {
			int id = Integer.parseInt(arg.toUpperCase(), 36);
			return (id >= 1 && id < 36 * 36) ? id : -1;
		}
	}

	private CommandWord(BiFunction<BMSModel, String, DecodeLog> function) {
		this.function = function;
	}	
}

/**
 * 予約語
 *
 * @author exch
 */
enum OptionWord {

	URL ((model, arg) -> {
		// TODO 未実装
		return null;
	});

	public final BiFunction<BMSModel, String, DecodeLog> function;

	private static int parseObjId(BMSModel model, String arg) {
		final String trimmed = arg.trim();
		if (trimmed.isEmpty()) {
			throw new NumberFormatException();
		}
		int spaceIdx = 0;
		while (spaceIdx < trimmed.length() && !Character.isWhitespace(trimmed.charAt(spaceIdx))) {
			spaceIdx++;
		}
		arg = trimmed.substring(0, spaceIdx);
		if (arg.isEmpty() || arg.length() > 2) {
			throw new NumberFormatException();
		}
		if (model.getBase() == 62) {
			if (arg.length() != 2) {
				throw new NumberFormatException();
			}
			return ChartDecoder.parseInt62(arg, 0);
		} else {
			int id = Integer.parseInt(arg.toUpperCase(), 36);
			return (id >= 1 && id < 36 * 36) ? id : -1;
		}
	}

	private OptionWord(BiFunction<BMSModel, String, DecodeLog> function) {
		this.function = function;
	}
}
