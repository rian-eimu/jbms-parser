package bms.model;

import java.util.*;
import java.util.Map.Entry;

import bms.model.ChartDecoder.TimeLineCache;
import bms.model.Layer.EventType;

import static bms.model.DecodeLog.State.*;

/**
 * 小節
 * 
 * @author exch
 */
public class Section {

	public static final int ILLEGAL = -1;
	public static final int LANE_AUTOPLAY = 1;
	public static final int SECTION_RATE = 2;
	public static final int BPM_CHANGE = 3;
	public static final int BGA_PLAY = 4;
	public static final int POOR_PLAY = 6;
	public static final int LAYER_PLAY = 7;
	public static final int BPM_CHANGE_EXTEND = 8;
	public static final int STOP = 9;

	public static final int P1_KEY_BASE = 1 * 36 + 1;
	public static final int P2_KEY_BASE = 2 * 36 + 1;
	public static final int P1_INVISIBLE_KEY_BASE = 3 * 36 + 1;
	public static final int P2_INVISIBLE_KEY_BASE = 4 * 36 + 1;
	public static final int P1_LONG_KEY_BASE = 5 * 36 + 1;
	public static final int P2_LONG_KEY_BASE = 6 * 36 + 1;
	public static final int P1_MINE_KEY_BASE = 13 * 36 + 1;
	public static final int P2_MINE_KEY_BASE = 14 * 36 + 1;
	public static final int P1_POTION_KEY_BASE = 15 * 36 + 1;
	public static final int P2_POTION_KEY_BASE = 16 * 36 + 1;
	public static final int P1_DUMMY_KEY_BASE = 17 * 36 + 1;
	public static final int P2_DUMMY_KEY_BASE = 18 * 36 + 1;
	public static final int P1_DUMMY_LONG_KEY_BASE = 19 * 36 + 1;
	public static final int P2_DUMMY_LONG_KEY_BASE = 20 * 36 + 1;
	public static final int P1_DUMMY_MINE_KEY_BASE = 21 * 36 + 1;
	public static final int P2_DUMMY_MINE_KEY_BASE = 22 * 36 + 1;
	public static final int P1_DUMMY_POTION_KEY_BASE = 23 * 36 + 1;
	public static final int P2_DUMMY_POTION_KEY_BASE = 24 * 36 + 1;

	public static final int SCROLL = 1020;
	public static final int P1_FREEZONE_LEGACY = 1 * 36 + 7;
	public static final int P2_FREEZONE_LEGACY = 2 * 36 + 7;
	public static final int P1_FREEZONE_SINGLE = 28 * 36 + 15;
	public static final int P1_FREEZONE_PAIR = 28 * 36 + 16;
	public static final int P2_FREEZONE_SINGLE = 28 * 36 + 17;
	public static final int P2_FREEZONE_PAIR = 28 * 36 + 18;
	public static final int SPEED = 1033;
	
	public static final int[] NOTE_CHANNELS = {P1_KEY_BASE, P2_KEY_BASE ,P1_INVISIBLE_KEY_BASE, P2_INVISIBLE_KEY_BASE, 
			P1_LONG_KEY_BASE, P2_LONG_KEY_BASE, P1_MINE_KEY_BASE, P2_MINE_KEY_BASE, P1_POTION_KEY_BASE, P2_POTION_KEY_BASE,
			P1_DUMMY_KEY_BASE, P2_DUMMY_KEY_BASE, P1_DUMMY_LONG_KEY_BASE, P2_DUMMY_LONG_KEY_BASE,
			P1_DUMMY_MINE_KEY_BASE, P2_DUMMY_MINE_KEY_BASE, P1_DUMMY_POTION_KEY_BASE, P2_DUMMY_POTION_KEY_BASE};

	/**
	 * 小節の拡大倍率
	 */
	private double rate = 1.0;
	/**
	 * POORアニメーション
	 */
	private int[] poor = new int[0];

	private final BMSModel model;

	private final double sectionnum;

	private final List<DecodeLog> log;
	
	private List<String> channellines;
	private final boolean legacyFreeZoneEnabled;

	public Section(BMSModel model, Section prev, List<String> lines, Map<Integer, Double> bpmtable,
			Map<Integer, Double> stoptable, Map<Integer, Double> scrolltable, Map<Integer, Double> speedtable, List<DecodeLog> log) {
		this(model, prev, lines, bpmtable, stoptable, scrolltable, speedtable, log, false);
	}

	public Section(BMSModel model, Section prev, List<String> lines, Map<Integer, Double> bpmtable,
			Map<Integer, Double> stoptable, Map<Integer, Double> scrolltable, Map<Integer, Double> speedtable, List<DecodeLog> log,
			boolean legacyFreeZoneEnabled) {
		this.legacyFreeZoneEnabled = legacyFreeZoneEnabled;
		this.model = model;
		this.log = log;
		final int base = model.getBase();
		
		channellines = new ArrayList<String>(lines.size());
		if (prev != null) {
			sectionnum = prev.sectionnum + prev.rate;
		} else {
			sectionnum = 0;
		}
		for (String line : lines) {
			int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
			switch (channel) {
			case ILLEGAL:
				log.add(new DecodeLog(WARNING, "チャンネル定義が無効です : " + line));
				break;
			// BGレーン
			case LANE_AUTOPLAY:
				// BGAレーン
			case BGA_PLAY:
			case P1_FREEZONE_SINGLE:
			case P1_FREEZONE_PAIR:
			case P2_FREEZONE_SINGLE:
			case P2_FREEZONE_PAIR:
			// レイヤー
			case LAYER_PLAY:
				channellines.add(line);
				break;
			// 小節の拡大率
			case SECTION_RATE:
				final int sindex = getDataStartIndex(line);
				if (sindex != -1) {
					try {
						rate = Double.valueOf(line.substring(sindex).trim());					
					} catch (NumberFormatException e) {
						log.add(new DecodeLog(WARNING, "小節の拡大率が不正です : " + line));
					}
				} else {
					log.add(new DecodeLog(WARNING, "小節の拡大率が不正です : " + line));
				}
				break;
			// BPM変化
			case BPM_CHANGE:
				this.processData(line, (pos, data) -> {
					if(base == 62) {
						data = ChartDecoder.parseInt36(ChartDecoder.toBase62(data), 0); //間違った数値を再計算、62進数文字に戻して36進数数値化。
					}
					bpmchange.put(pos, (double) (data / 36) * 16 + (data % 36));
				});
				break;
			// POORアニメーション
			case POOR_PLAY:
				poor = this.splitData(line);
				// アニメーションが単一画像のみの定義の場合、0を除外する(ミスレイヤーチャンネルの定義が曖昧)
				int singleid = 0;
				for(int id : poor) {
					if(id != 0) {
						if(singleid != 0 && singleid != id) {
							singleid = -1;
							break;
						} else {
							singleid = id;
						}
					}
				}
				if(singleid != -1) {
					poor = new int[] {singleid};
				}
				break;
			// BPM変化(拡張)
			case BPM_CHANGE_EXTEND:
				this.processData(line, (pos, data) -> {
					Double bpm = bpmtable.get(data);
					if (bpm != null) {
						bpmchange.put(pos, bpm);
					} else {
						log.add(new DecodeLog(WARNING, "未定義のBPM変化を参照しています : " + data));
					}
				});
				break;
			// ストップシーケンス
			case STOP:
				this.processData(line, (pos, data) -> {
					Double st = stoptable.get(data);
					if (st != null) {
						stop.put(pos, st);
					} else {
						log.add(new DecodeLog(WARNING, "未定義のSTOPを参照しています : " + data));
					}
				});
				break;
				// scroll
			case SCROLL:
				this.processData(line, (pos, data) -> {
					Double st = scrolltable.get(data);
					if (st != null) {
						scroll.put(pos, st);
					} else {
						log.add(new DecodeLog(WARNING, "未定義のSCROLLを参照しています : " + data));
					}
				});
				break;
			case SPEED:
				this.processData(line, (pos, data) -> {
					Double sp = speedtable.get(data);
					if (sp != null) {
						speed.put(pos, sp);
					} else {
						log.add(new DecodeLog(WARNING, "未定義のSPEEDを参照しています : " + data));
					}
				});
				break;
			}
			
			int basech = 0;
			int ch2 = -1;
			for(int ch : NOTE_CHANNELS) {
				if (ch <= channel && channel <= ch + 8) {
					basech = ch;
					ch2 = channel - ch;					
					channellines.add(line);
					break;
				}				
			}
			// 5/10KEY -> 7/14KEY
			if(ch2 == 7 || ch2 == 8) {
				final Mode mode = (model.getMode() == Mode.BEAT_5K) ? Mode.BEAT_7K : (model.getMode() == Mode.BEAT_10K ? Mode.BEAT_14K : null);
				if(mode != null) {
					this.processData(line, (pos, data) -> {
						model.setMode(mode);
					});
				}
			}
			// 5/7KEY -> 10/14KEY			
			if (((basech == P2_KEY_BASE || basech == P2_INVISIBLE_KEY_BASE || basech == P2_LONG_KEY_BASE || basech == P2_MINE_KEY_BASE || basech == P2_POTION_KEY_BASE
					|| basech == P2_DUMMY_KEY_BASE || basech == P2_DUMMY_LONG_KEY_BASE || basech == P2_DUMMY_MINE_KEY_BASE || basech == P2_DUMMY_POTION_KEY_BASE)
					&& (channel != P2_FREEZONE_LEGACY || legacyFreeZoneEnabled))
					|| channel == P2_FREEZONE_SINGLE || channel == P2_FREEZONE_PAIR) {
				final Mode mode = (model.getMode() == Mode.BEAT_5K) ? Mode.BEAT_10K : (model.getMode() == Mode.BEAT_7K ? Mode.BEAT_14K : null);
				if(mode != null) {
					this.processData(line, (pos, data) -> {
						model.setMode(mode);
					});
				}
			}			
		}
	}
	
	/**
	 * 小節データ行のデータ開始インデックスを取得する。
	 * 通常のコロン区切り (#xxxyy:data) およびスペース区切り (#xxxyy data) に対応。
	 * 
	 * @param line 小節データ行
	 * @return データ部の開始インデックス。区切り文字が存在しない場合は -1
	 */
	private static int getDataStartIndex(String line) {
		int findex = 6;
		final int len = line.length();
		boolean foundSeparator = false;
		while (findex < len) {
			final char c = line.charAt(findex);
			if (c == ':' || Character.isWhitespace(c)) {
				foundSeparator = true;
				findex++;
			} else {
				break;
			}
		}
		return foundSeparator ? findex : -1;
	}

	/**
	 * 小節データ行のデータ終了インデックスを取得する（末尾の空白文字を除去）。
	 * 
	 * @param line 小節データ行
	 * @param findex データ部の開始インデックス
	 * @return データ部の終了インデックス
	 */
	private static int getDataEndIndex(String line, int findex) {
		int lindex = line.length();
		while (lindex > findex && Character.isWhitespace(line.charAt(lindex - 1))) {
			lindex--;
		}
		return lindex;
	}

	private int[] splitData(String line) {
		final int base = model.getBase();
		final int findex = getDataStartIndex(line);
		if (findex == -1) {
			log.add(new DecodeLog(WARNING, model.getTitle() + ":チャンネル定義の区切り文字が不正です:" + line));
			return new int[0];
		}
		final int lindex = getDataEndIndex(line, findex);
		final int split = (lindex - findex) / 2;
		int[] result = new int[split];
		for (int i = 0; i < split; i++) {
			if (base == 62) {
				result[i] = ChartDecoder.parseInt62(line.charAt(findex + i * 2), line.charAt(findex + i * 2 + 1));
			} else {
				result[i] = ChartDecoder.parseInt36(line.charAt(findex + i * 2), line.charAt(findex + i * 2 + 1));
			}
			if(result[i] == -1) {
				log.add(new DecodeLog(WARNING, model.getTitle() + ":チャンネル定義中の不正な値:" + line));
				result[i] = 0;
			}
		}
		return result;			
	}
	
	private void processData(String line, DataProcessor processor) {
		final int base = model.getBase();
		final int findex = getDataStartIndex(line);
		if (findex == -1) {
			log.add(new DecodeLog(WARNING, model.getTitle() + ":チャンネル定義の区切り文字が不正です:" + line));
			return;
		}
		final int lindex = getDataEndIndex(line, findex);
		final int split = (lindex - findex) / 2;
		int result;
		for (int i = 0; i < split; i++) {
			if (base == 62) {
				result = ChartDecoder.parseInt62(line.charAt(findex + i * 2), line.charAt(findex + i * 2 + 1));
			} else {
				result = ChartDecoder.parseInt36(line.charAt(findex + i * 2), line.charAt(findex + i * 2 + 1));
			}
			if(result > 0) {
				final int g = gcd(i, split);
				processor.process((double)(i / g) / (split / g), result);
			} else if(result == -1){
				log.add(new DecodeLog(WARNING, model.getTitle() + ":チャンネル定義中の不正な値:" + line));				
			}
		}
	}
	
	private static int gcd(int a, int b) {
		while (b != 0) {
			int t = a % b;
			a = b;
			b = t;
		}
		return a == 0 ? 1 : a;
	}

	private static class LanePosEvents {
		final List<Integer> longNotes = new ArrayList<Integer>(2);
		final List<Integer> keyNotes = new ArrayList<Integer>(2);
		final List<Integer> mineNotes = new ArrayList<Integer>(1);
		final List<Integer> potionNotes = new ArrayList<Integer>(1);
		final List<Integer> dummyLongNotes = new ArrayList<Integer>(2);
		final List<Integer> dummyKeyNotes = new ArrayList<Integer>(2);
		final List<Integer> dummyMineNotes = new ArrayList<Integer>(1);
		final List<Integer> dummyPotionNotes = new ArrayList<Integer>(1);
	}

	interface DataProcessor {
		public void process(double pos, int data);
	}

	private final TreeMap<Double, Double> bpmchange = new TreeMap<Double, Double>();
	private final TreeMap<Double, Double> stop = new TreeMap<Double, Double>();
	private final TreeMap<Double, Double> scroll = new TreeMap<Double, Double>();
	private final TreeMap<Double, Double> speed = new TreeMap<Double, Double>();
	
	private static final int[] CHANNELASSIGN_BEAT5 = { 0, 1, 2, 3, 4, 5, -1, -1, -1, 6, 7, 8, 9, 10, 11, -1, -1, -1 };
	private static final int[] CHANNELASSIGN_BEAT7 = { 0, 1, 2, 3, 4, 7, -1, 5, 6, 8, 9, 10, 11, 12, 15, -1, 13, 14 };
	private static final int[] CHANNELASSIGN_POPN = { 0, 1, 2, 3, 4, 7, 8, 5, 6, -1, 5, 6, 7, 8, -1, -1, -1, -1 };

	private TreeMap<Double, TimeLineCache> tlcache;

	/**
	 * SectionモデルからTimeLineモデルを作成し、BMSModelに登録する
	 */
	public void makeTimeLines(int[] wavmap, int[] bgamap, TreeMap<Double, TimeLineCache> tlcache, List<LongNote>[] lnlist, LongNote[] startln) {
		makeTimeLines(wavmap, bgamap, tlcache, lnlist, startln, new LongNote[model.getMode().key]);
	}

	public void makeTimeLines(int[] wavmap, int[] bgamap, TreeMap<Double, TimeLineCache> tlcache, List<LongNote>[] lnlist, LongNote[] startln, LongNote[] dummystartln) {
		final int lnobj = model.getLnobj();
		final int cnobj = model.getCnobj();
		final int hcnobj = model.getHcnobj();
		final int hlnobj = model.getHlnobj();
		final int lnmode = model.getLnmode();
		this.tlcache = tlcache;
		final int[] cassign = model.getMode() == Mode.POPN_9K ? CHANNELASSIGN_POPN : 
			(model.getMode() == Mode.BEAT_7K || model.getMode() == Mode.BEAT_14K ? CHANNELASSIGN_BEAT7 : CHANNELASSIGN_BEAT5);
		final int base = model.getBase();
		// 小節線追加
		final TimeLine basetl = getTimeLine(sectionnum);
		basetl.setSectionLine(true);
		
		if(poor.length > 0) {
			final Layer.Sequence[] poors = new Layer.Sequence[poor.length + 1];
			final int poortime = 500;
			
			for (int i = 0; i < poor.length; i++) {
				if (bgamap[poor[i]] != -2) {
					poors[i] = new Layer.Sequence((long)(i * poortime / poor.length), bgamap[poor[i]]);
				} else {
					poors[i] = new Layer.Sequence((long)(i * poortime / poor.length), -1);
				}
			}
			poors[poors.length - 1] = new Layer.Sequence(poortime);
			basetl.setEventlayer(new Layer[] {new Layer(new Layer.Event(EventType.MISS, 1),new Layer.Sequence[][] {poors})});			
		}
		// BPM変化。ストップシーケンステーブル準備
		Iterator<Entry<Double, Double>> stops = stop.entrySet().iterator();			
		Map.Entry<Double, Double> ste = stops.hasNext() ? stops.next() : null;
		Iterator<Entry<Double, Double>> bpms = bpmchange.entrySet().iterator();			
		Map.Entry<Double, Double> bce = bpms.hasNext() ? bpms.next() : null;
		Iterator<Entry<Double, Double>> scrolls = scroll.entrySet().iterator();			
		Map.Entry<Double, Double> sce = scrolls.hasNext() ? scrolls.next() : null;
		Iterator<Entry<Double, Double>> speeds = speed.entrySet().iterator();
		Map.Entry<Double, Double> spe = speeds.hasNext() ? speeds.next() : null;
		
		while(ste != null || bce != null || sce != null || spe != null) {
			final double bc = bce != null ? bce.getKey() : 2;
			final double st = ste != null ? ste.getKey() : 2;
			final double sc = sce != null ? sce.getKey() : 2;
			final double sp = spe != null ? spe.getKey() : 2;
			if(sp <= sc && sp <= st && sp <= bc) {
				getTimeLine(sectionnum + sp * rate).setSpeedObj(spe.getValue());
				spe = speeds.hasNext() ? speeds.next() : null;
			} else if(sc <= st && sc <= bc) {
				getTimeLine(sectionnum + sc * rate).setScroll(sce.getValue());
				sce = scrolls.hasNext() ? scrolls.next() : null;
			} else if(bc <= st) {
				getTimeLine(sectionnum + bc * rate).setBPM(bce.getValue());
				bce = bpms.hasNext() ? bpms.next() : null;
			} else if(st <= 1){
				final TimeLine tl = getTimeLine(sectionnum + ste.getKey() * rate);
				tl.setStop((long) (1000.0 * 1000 * 60 * 4 * ste.getValue() / (tl.getBPM())));
				ste = stops.hasNext() ? stops.next() : null;
			}
		}
		
		processChannelLines(cassign, wavmap, bgamap, lnlist, startln, dummystartln, lnobj, cnobj, hcnobj, hlnobj, lnmode, base);
		if (cassign != null) {
			return;
		}
		final int numKeys = model.getMode().key;
		final TreeMap<Double, LanePosEvents[]> posEvents = new TreeMap<Double, LanePosEvents[]>();
		for(String line : channellines) {
			int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
			int tmpkey = 0;
			if(channel >= P1_KEY_BASE && channel < P1_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_KEY_BASE];
				channel = P1_KEY_BASE;
			} else if(channel >= P2_KEY_BASE && channel < P2_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_KEY_BASE + 9];
				channel = P1_KEY_BASE;
			} else if(channel >= P1_INVISIBLE_KEY_BASE && channel < P1_INVISIBLE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_INVISIBLE_KEY_BASE];
				channel = P1_INVISIBLE_KEY_BASE;
			} else if(channel >= P2_INVISIBLE_KEY_BASE && channel < P2_INVISIBLE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_INVISIBLE_KEY_BASE + 9];
				channel = P1_INVISIBLE_KEY_BASE;
			} else if(channel >= P1_LONG_KEY_BASE && channel < P1_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_LONG_KEY_BASE];
				channel = P1_LONG_KEY_BASE;
			} else if(channel >= P2_LONG_KEY_BASE && channel < P2_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_LONG_KEY_BASE + 9];
				channel = P1_LONG_KEY_BASE;
			} else if(channel >= P1_MINE_KEY_BASE && channel < P1_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_MINE_KEY_BASE];
				channel = P1_MINE_KEY_BASE;
			} else if(channel >= P2_MINE_KEY_BASE && channel < P2_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_MINE_KEY_BASE + 9];
				channel = P1_MINE_KEY_BASE;
			}
			final int key = tmpkey;
			if(key == -1) {
				continue;
			}
			switch (channel) {
			case P1_KEY_BASE:
				this.processData(line, (pos, data) -> recordKeyNote(posEvents, numKeys, key, pos, data));
				if (posEvents != null) break;
				this.processData(line, (pos, data) -> {
					// normal note, lnobj
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					if (tl.existNote(key)) {
						log.add(new DecodeLog(WARNING, "通常ノート追加時に衝突が発生しました : " + (key + 1) + ":"
								+ tl.getTime()));
					}
					final int objType = lnmode;
					if (data == lnobj) {
						// LN終端処理
						// TODO 高速化のために直前のノートを記録しておく
						for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
							if(e.getKey() >= tl.getSection() ) {
								continue;
							}
							final TimeLine tl2 = e.getValue().timeline;
							if (tl2.existNote(key)) {
								final Note note = tl2.getNote(key);
								if (note instanceof NormalNote) {
									// LNOBJの直前のノートをLNに差し替える
									LongNote ln = new LongNote(note.getWav());
									ln.setType(objType);
									for (Note layered : note.getLayeredNotes()) {
										ln.addLayeredNote(layered);
									}
									tl2.setNote(key, ln);
									LongNote lnend = new LongNote(-2);
									tl.setNote(key, lnend);

									ln.setPair(lnend);
									
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<LongNote>();
									}
									lnlist[key].add(ln);
									break;
								} else if (note instanceof LongNote && ((LongNote) note).getPair() == null) {
									log.add(new DecodeLog(WARNING,
											"LNレーンで開始定義し、LNオブジェクトで終端定義しています。レーン: " + (key + 1) + " - Section : "
													+ tl2.getSection()  + " - " + tl.getSection()));
									LongNote lnend = new LongNote(-2);
									tl.setNote(key, lnend);

									((LongNote) note).setType(objType);
									((LongNote) note).setPair(lnend);
									
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<LongNote>();
									}
									lnlist[key].add((LongNote) note);
									startln[key] = null;
									break;
								} else {
									log.add(new DecodeLog(WARNING, "LNオブジェクトの対応が取れません。レーン: " + key
											+ " - Time(ms):" + tl2.getTime()));
									break;
								}
							}
						}
					} else {
						tl.setNote(key, new NormalNote(wavmap[data]));
					}							
				});
				break;				
			case P1_INVISIBLE_KEY_BASE:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setHiddenNote(key, new NormalNote(wavmap[data]));
				});
				break;				
			case P1_LONG_KEY_BASE:
				this.processData(line, (pos, data) -> recordLongNote(posEvents, numKeys, key, pos, data));
				if (posEvents != null) break;
				this.processData(line, (pos, data) -> {
					// long note
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					boolean insideln = false;
					if (!insideln && lnlist[key] != null) {
						final double section = tl.getSection();
						for (LongNote ln : lnlist[key]) {
							if (ln.getSection() <= section && section <= ln.getPair().getSection()) {
								insideln = true;
								break;
							}
						}
					}

					if(!insideln) {
						// LN処理
						if (startln[key] == null) {
							if(tl.existNote(key)) {
								Note note = tl.getNote(key);
								log.add(new DecodeLog(WARNING, "LN開始位置に通常ノートが存在します。レーン: "
										+ (key + 1) + " - Time(ms):" + tl.getTime()));
								if(note instanceof NormalNote && note.getWav() != wavmap[data]) {
									tl.addBackGroundNote(note);
								}								
							}
							LongNote ln = new LongNote(wavmap[data]);
							tl.setNote(key, ln);
							startln[key] = ln;
						} else if(startln[key].getSection() == Double.MIN_VALUE){
							startln[key] = null;
						} else {
							// LN終端処理
							for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
								if(e.getKey() >= tl.getSection()) {
									continue;
								}
								
								final TimeLine tl2 = e.getValue().timeline;									
								if(tl2.getSection() == startln[key].getSection()) {
									Note note = startln[key];
									((LongNote)note).setType(lnmode);
									LongNote noteend = new LongNote(startln[key].getWav() != wavmap[data] ? wavmap[data] : -2);
									tl.setNote(key, noteend);
									((LongNote)note).setPair(noteend);
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<LongNote>();
									}
									lnlist[key].add((LongNote) note);											
									
									startln[key] = null;
									break;										
								} else if(tl2.existNote(key)){
									Note note = tl2.getNote(key);
									log.add(new DecodeLog(WARNING, "LN内に通常ノートが存在します。レーン: "
											+ (key + 1) + " - Time(ms):" + tl2.getTime()));
									tl2.setNote(key, null);
									if(note instanceof NormalNote) {
										tl2.addBackGroundNote(note);
									}
								}										
							}
						}								
					} else {
						if (startln[key] == null) {
							LongNote ln = new LongNote(wavmap[data]);
							ln.setSection(Double.MIN_VALUE);
							startln[key] = ln;
							log.add(new DecodeLog(WARNING, "LN内にLN開始ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						} else {
							if(startln[key].getSection() != Double.MIN_VALUE) {
								tlcache.get(startln[key].getSection()).timeline.setNote(key,  null);
							}
							startln[key] = null;										
							log.add(new DecodeLog(WARNING, "LN内にLN終端ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						}
					}
				});
				break;				
			case P1_DUMMY_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyKeyNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_LONG_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyLongNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_MINE_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyMineNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_POTION_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyPotionNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_POTION_KEY_BASE:
				this.processData(line, (pos, data) -> recordPotionNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_MINE_KEY_BASE:
				this.processData(line, (pos, data) -> recordMineNote(posEvents, numKeys, key, pos, data));
				if (posEvents != null) break;
				// mine note
				this.processData(line, (pos, data) -> {
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					boolean insideln = tl.existNote(key);
					if (!insideln && lnlist[key] != null) {
						final double section = tl.getSection();
						for (LongNote ln : lnlist[key]) {
							if (ln.getSection() <= section && section <= ln.getPair().getSection()) {
								insideln = true;
								break;
							}
						}
					}

					if(!insideln) {
						if(base == 62) {
							data = ChartDecoder.parseInt36(ChartDecoder.toBase62(data), 0); //間違った数値を再計算、62進数文字に戻して36進数数値化。
						}
						tl.setNote(key, new MineNote(wavmap[0], data));								
					} else {
						log.add(new DecodeLog(WARNING, "地雷ノート追加時に衝突が発生しました : " + (key + 1) + ":"
								+ tl.getTime()));								
					}
				});
				break;
			case LANE_AUTOPLAY:
				// BGレーン
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).addBackGroundNote(new NormalNote(wavmap[data]));
				});
				break;
			case BGA_PLAY:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setBGA(bgamap[data]);
				});
				break;
			case LAYER_PLAY:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setLayer(bgamap[data]);
				});
				break;

			}
		}
	}
	
	private static void recordKeyNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].keyNotes.add(data);
	}

	private static void recordLongNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].longNotes.add(data);
	}

	private static void recordMineNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].mineNotes.add(data);
	}

	private static void recordPotionNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].potionNotes.add(data);
	}

	private static void recordDummyKeyNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].dummyKeyNotes.add(data);
	}

	private static void recordDummyLongNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].dummyLongNotes.add(data);
	}

	private static void recordDummyMineNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].dummyMineNotes.add(data);
	}

	private static void recordDummyPotionNote(TreeMap<Double, LanePosEvents[]> posEvents, int numKeys, int key, double pos, int data) {
		LanePosEvents[] arr = posEvents.computeIfAbsent(pos, k -> new LanePosEvents[numKeys]);
		if (arr[key] == null) {
			arr[key] = new LanePosEvents();
		}
		arr[key].dummyPotionNotes.add(data);
	}

	private void processChannelLines(int[] cassign, int[] wavmap, int[] bgamap, List<LongNote>[] lnlist, LongNote[] startln, LongNote[] dummystartln,
			int lnobj, int cnobj, int hcnobj, int hlnobj, int lnmode, int base) {
		final int numKeys = model.getMode().key;
		final TreeMap<Double, LanePosEvents[]> posEvents = new TreeMap<Double, LanePosEvents[]>();
		for (String line : channellines) {
			int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
			int tmpkey = 0;
			if (channel >= P1_KEY_BASE && channel < P1_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_KEY_BASE];
				channel = P1_KEY_BASE;
			} else if (channel >= P2_KEY_BASE && channel < P2_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_KEY_BASE + 9];
				channel = P1_KEY_BASE;
			} else if (channel >= P1_INVISIBLE_KEY_BASE && channel < P1_INVISIBLE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_INVISIBLE_KEY_BASE];
				channel = P1_INVISIBLE_KEY_BASE;
			} else if (channel >= P2_INVISIBLE_KEY_BASE && channel < P2_INVISIBLE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_INVISIBLE_KEY_BASE + 9];
				channel = P1_INVISIBLE_KEY_BASE;
			} else if (channel >= P1_LONG_KEY_BASE && channel < P1_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_LONG_KEY_BASE];
				channel = P1_LONG_KEY_BASE;
			} else if (channel >= P2_LONG_KEY_BASE && channel < P2_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_LONG_KEY_BASE + 9];
				channel = P1_LONG_KEY_BASE;
			} else if (channel >= P1_MINE_KEY_BASE && channel < P1_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_MINE_KEY_BASE];
				channel = P1_MINE_KEY_BASE;
			} else if (channel >= P1_POTION_KEY_BASE && channel < P1_POTION_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_POTION_KEY_BASE];
				channel = P1_POTION_KEY_BASE;
			} else if (channel >= P2_POTION_KEY_BASE && channel < P2_POTION_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_POTION_KEY_BASE + 9];
				channel = P1_POTION_KEY_BASE;
			} else if (channel >= P1_DUMMY_KEY_BASE && channel < P1_DUMMY_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_DUMMY_KEY_BASE];
				channel = P1_DUMMY_KEY_BASE;
			} else if (channel >= P2_DUMMY_KEY_BASE && channel < P2_DUMMY_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_DUMMY_KEY_BASE + 9];
				channel = P1_DUMMY_KEY_BASE;
			} else if (channel >= P1_DUMMY_LONG_KEY_BASE && channel < P1_DUMMY_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_DUMMY_LONG_KEY_BASE];
				channel = P1_DUMMY_LONG_KEY_BASE;
			} else if (channel >= P2_DUMMY_LONG_KEY_BASE && channel < P2_DUMMY_LONG_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_DUMMY_LONG_KEY_BASE + 9];
				channel = P1_DUMMY_LONG_KEY_BASE;
			} else if (channel >= P1_DUMMY_MINE_KEY_BASE && channel < P1_DUMMY_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_DUMMY_MINE_KEY_BASE];
				channel = P1_DUMMY_MINE_KEY_BASE;
			} else if (channel >= P2_DUMMY_MINE_KEY_BASE && channel < P2_DUMMY_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_DUMMY_MINE_KEY_BASE + 9];
				channel = P1_DUMMY_MINE_KEY_BASE;
			} else if (channel >= P1_DUMMY_POTION_KEY_BASE && channel < P1_DUMMY_POTION_KEY_BASE + 9) {
				tmpkey = cassign[channel - P1_DUMMY_POTION_KEY_BASE];
				channel = P1_DUMMY_POTION_KEY_BASE;
			} else if (channel >= P2_DUMMY_POTION_KEY_BASE && channel < P2_DUMMY_POTION_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_DUMMY_POTION_KEY_BASE + 9];
				channel = P1_DUMMY_POTION_KEY_BASE;
			} else if (channel >= P2_MINE_KEY_BASE && channel < P2_MINE_KEY_BASE + 9) {
				tmpkey = cassign[channel - P2_MINE_KEY_BASE + 9];
				channel = P1_MINE_KEY_BASE;
			}
			final int key = tmpkey;
			if (key == -1) {
				continue;
			}
			switch (channel) {
			case P1_KEY_BASE:
				this.processData(line, (pos, data) -> recordKeyNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_INVISIBLE_KEY_BASE:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setHiddenNote(key, new NormalNote(wavmap[data]));
				});
				break;
			case P1_LONG_KEY_BASE:
				this.processData(line, (pos, data) -> recordLongNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyKeyNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_LONG_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyLongNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_MINE_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyMineNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_DUMMY_POTION_KEY_BASE:
				this.processData(line, (pos, data) -> recordDummyPotionNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_POTION_KEY_BASE:
				this.processData(line, (pos, data) -> recordPotionNote(posEvents, numKeys, key, pos, data));
				break;
			case P1_MINE_KEY_BASE:
				this.processData(line, (pos, data) -> recordMineNote(posEvents, numKeys, key, pos, data));
				break;
			case LANE_AUTOPLAY:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).addBackGroundNote(new NormalNote(wavmap[data]));
				});
				break;
			case BGA_PLAY:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setBGA(bgamap[data]);
				});
				break;
			case LAYER_PLAY:
				this.processData(line, (pos, data) -> {
					getTimeLine(sectionnum + rate * pos).setLayer(bgamap[data]);
				});
				break;
			}
		}

		for (Map.Entry<Double, LanePosEvents[]> posEntry : posEvents.entrySet()) {
			final double pos = posEntry.getKey();
			final LanePosEvents[] arr = posEntry.getValue();
			for (int key = 0; key < numKeys; key++) {
				final LanePosEvents ev = arr[key];
				if (ev == null) {
					continue;
				}
				// 1. 専用LNチャネル (5x/6x) を先に処理して優先させる
				for (int data : ev.longNotes) {
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					boolean insideln = false;
					if (lnlist[key] != null) {
						final double section = tl.getSection();
						for (LongNote ln : lnlist[key]) {
							if (ln.getSection() <= section && section <= ln.getPair().getSection()) {
								insideln = true;
								break;
							}
						}
					}

					if (!insideln) {
						if (startln[key] == null) {
							if (tl.existNote(key)) {
								Note note = tl.getNote(key);
								log.add(new DecodeLog(WARNING, "LN開始位置に通常ノートが存在します。レーン: "
										+ (key + 1) + " - Time(ms):" + tl.getTime()));
								if (note instanceof NormalNote && !note.isDummy() && note.getWav() != wavmap[data]) {
									tl.addBackGroundNote(note);
								}
							}
							LongNote ln = new LongNote(wavmap[data]);
							tl.setNote(key, ln);
							startln[key] = ln;
						} else if (startln[key].getSection() == Double.MIN_VALUE) {
							startln[key] = null;
						} else {
							for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
								if (e.getKey() >= tl.getSection()) {
									continue;
								}
								final TimeLine tl2 = e.getValue().timeline;
								if (tl2.getSection() == startln[key].getSection()) {
									Note note = startln[key];
									((LongNote) note).setType(lnmode);
									LongNote noteend = new LongNote(startln[key].getWav() != wavmap[data] ? wavmap[data] : -2);
									tl.setNote(key, noteend);
									((LongNote) note).setPair(noteend);
									if (lnlist[key] == null) {
										lnlist[key] = new ArrayList<LongNote>();
									}
									lnlist[key].add((LongNote) note);
									startln[key] = null;
									break;
								} else if (tl2.existNote(key)) {
									Note note = tl2.getNote(key);
									log.add(new DecodeLog(WARNING, "LN内に通常ノートが存在します。レーン: "
											+ (key + 1) + " - Time(ms):" + tl2.getTime()));
									tl2.setNote(key, null);
									if (note instanceof NormalNote && !note.isDummy()) {
										tl2.addBackGroundNote(note);
									}
								}
							}
						}
					} else {
						if (startln[key] == null) {
							LongNote ln = new LongNote(wavmap[data]);
							ln.setSection(Double.MIN_VALUE);
							startln[key] = ln;
							log.add(new DecodeLog(WARNING, "LN内にLN開始ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						} else {
							if (startln[key].getSection() != Double.MIN_VALUE) {
								tlcache.get(startln[key].getSection()).timeline.setNote(key, null);
							}
							startln[key] = null;
							log.add(new DecodeLog(WARNING, "LN内にLN終端ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						}
					}
				}

				// 2. 通常ノーツ・#xxxOBJマーカー (1x/2x) を処理する
				if (!ev.keyNotes.isEmpty()) {
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					final int existingCount = tl.existNote(key) ? 1 : 0;
					for (int i = 0; i < ev.keyNotes.size(); i++) {
						if (existingCount + i > 0) {
							log.add(new DecodeLog(WARNING, "通常ノート追加時に衝突が発生しました : " + (key + 1) + ":"
									+ tl.getTime()));
						}
					}

					int maxMarkerPriority = 0;
					int lastNormalData = -1;
					TreeSet<Integer> uniqueNormalIds = null;
					for (int data : ev.keyNotes) {
						final int prio;
						if (data == hlnobj) {
							prio = 4;
						} else if (data == hcnobj) {
							prio = 3;
						} else if (data == cnobj) {
							prio = 2;
						} else if (data == lnobj) {
							prio = 1;
						} else {
							prio = 0;
						}
						if (prio > 0) {
							if (prio > maxMarkerPriority) {
								maxMarkerPriority = prio;
							}
						} else {
							lastNormalData = data;
							if (uniqueNormalIds == null) {
								uniqueNormalIds = new TreeSet<Integer>();
							}
							uniqueNormalIds.add(data);
						}
					}

					final Note existingAtTl = tl.getNote(key);
					if (maxMarkerPriority > 0) {
						final int objType = switch (maxMarkerPriority) {
							case 4 -> LongNote.TYPE_HELLLONGNOTE;
							case 3 -> LongNote.TYPE_HELLCHARGENOTE;
							case 2 -> LongNote.TYPE_CHARGENOTE;
							default -> lnmode;
						};
						if (existingAtTl instanceof LongNote existingLn && existingLn.isEnd() && existingLn.getPair() != null) {
							// 既存の専用LN終点と同位置のマーカーはペアを変えず、重ねた通常ノーツのWAVのみID昇順で終点に付与する
							if (uniqueNormalIds != null) {
								for (int normId : uniqueNormalIds) {
									addLayeredWavToEndNote(existingLn, wavmap[normId]);
								}
							}
						} else if (existingAtTl != null || isInsideExistingLongNote(lnlist[key], tl.getSection())
								|| (startln[key] != null && maxMarkerPriority > 1)) {
							log.add(new DecodeLog(WARNING, "LNオブジェクトの対応が取れません。レーン: " + key
									+ " - Time(ms):" + tl.getTime()));
						} else {
							boolean foundPreceding = false;
							for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
								if (e.getKey() >= tl.getSection()) {
									continue;
								}
								final TimeLine tl2 = e.getValue().timeline;
								if (tl2.existNote(key) && !tl2.getNote(key).isDummy()) {
									for (Map.Entry<Double, TimeLineCache> e2 : tlcache.subMap(tl2.getSection(), false, tl.getSection(), false).entrySet()) {
										if (e2.getValue().timeline.existNote(key) && e2.getValue().timeline.getNote(key).isDummy()) {
											e2.getValue().timeline.setNote(key, null);
										}
									}
									foundPreceding = true;
									final Note note = tl2.getNote(key);
									if (note instanceof NormalNote && startln[key] == null) {
										LongNote ln = new LongNote(note.getWav());
										ln.setType(objType);
										for (Note layered : note.getLayeredNotes()) {
											ln.addLayeredNote(layered);
										}
										tl2.setNote(key, ln);
										LongNote lnend = new LongNote(-2);
										if (uniqueNormalIds != null) {
											for (int normId : uniqueNormalIds) {
												addLayeredWavToEndNote(lnend, wavmap[normId]);
											}
										}
										tl.setNote(key, lnend);
										ln.setPair(lnend);
										if (lnlist[key] == null) {
											lnlist[key] = new ArrayList<LongNote>();
										}
										lnlist[key].add(ln);
									} else if (maxMarkerPriority == 1 && note instanceof LongNote && ((LongNote) note).getPair() == null) {
										log.add(new DecodeLog(WARNING,
												"LNレーンで開始定義し、LNオブジェクトで終端定義しています。レーン: " + (key + 1) + " - Section : "
														+ tl2.getSection() + " - " + tl.getSection()));
										LongNote lnend = new LongNote(-2);
										if (uniqueNormalIds != null) {
											for (int normId : uniqueNormalIds) {
												addLayeredWavToEndNote(lnend, wavmap[normId]);
											}
										}
										tl.setNote(key, lnend);
										((LongNote) note).setType(objType);
										((LongNote) note).setPair(lnend);
										if (lnlist[key] == null) {
											lnlist[key] = new ArrayList<LongNote>();
										}
										lnlist[key].add((LongNote) note);
										startln[key] = null;
									} else {
										log.add(new DecodeLog(WARNING, "LNオブジェクトの対応が取れません。レーン: " + key
												+ " - Time(ms):" + tl2.getTime()));
									}
									break;
								}
							}
							if (!foundPreceding) {
								log.add(new DecodeLog(WARNING, "LNオブジェクトの対応が取れません。レーン: " + key
										+ " - Time(ms):" + tl.getTime()));
							}
						}
					} else if (lastNormalData >= 0) {
						if (existingAtTl instanceof LongNote existingLn) {
							if (!existingLn.isEnd()) {
								log.add(new DecodeLog(WARNING, "LN開始位置に通常ノートが存在します。レーン: "
										+ (key + 1) + " - Time(ms):" + tl.getTime()));
								if (wavmap[lastNormalData] != existingLn.getWav()) {
									tl.addBackGroundNote(new NormalNote(wavmap[lastNormalData]));
								}
							}
						} else if (isInsideExistingLongNote(lnlist[key], tl.getSection())) {
							log.add(new DecodeLog(WARNING, "LN内に通常ノートが存在します。レーン: "
									+ (key + 1) + " - Time(ms):" + tl.getTime()));
							tl.addBackGroundNote(new NormalNote(wavmap[lastNormalData]));
						} else {
							// マーカーのない通常ノーツ重複は既存の後勝ち規則を維持する
							tl.setNote(key, new NormalNote(wavmap[lastNormalData]));
						}
					}
				}


				// 3. 地雷ノーツ (Dx/Ex) および 4. ポーションノーツ (Fx/Gx) を処理する
				for (int noteType = 0; noteType < 2; noteType++) {
					for (int rawData : (noteType == 0 ? ev.mineNotes : ev.potionNotes)) {
						int data = rawData;
						final TimeLine tl = getTimeLine(sectionnum + rate * pos);
						boolean insideln = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection());
						if (!insideln) {
							if (base == 62) {
								data = ChartDecoder.parseInt36(ChartDecoder.toBase62(data), 0);
							}
							tl.setNote(key, noteType == 0 ? new MineNote(wavmap[0], data) : new PotionNote(-1, data));
						} else {
							log.add(new DecodeLog(WARNING, (noteType == 0 ? "地雷ノート" : "ポーションノート")
									+ "追加時に衝突が発生しました : " + (key + 1) + ":" + tl.getTime()));
						}
					}
				}

				// 5. ダミーLNチャネル (Jx/Kx) を処理する
				for (int ignored : ev.dummyLongNotes) {
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					final boolean insideln = isInsideExistingLongNote(lnlist[key], tl.getSection()) || startln[key] != null;
					if (!insideln) {
						if (dummystartln[key] == null) {
							if (tl.existNote(key)) {
								LongNote ln = new LongNote(-2);
								ln.setDummy(true);
								ln.setSection(Double.MIN_VALUE);
								dummystartln[key] = ln;
								log.add(new DecodeLog(WARNING, "ダミーLN開始位置にノートが存在します。レーン: "
										+ (key + 1) + " - Time(ms):" + tl.getTime()));
							} else {
								LongNote ln = new LongNote(-2);
								ln.setDummy(true);
								tl.setNote(key, ln);
								dummystartln[key] = ln;
							}
						} else if (dummystartln[key].getSection() == Double.MIN_VALUE) {
							dummystartln[key] = null;
						} else if (tl.existNote(key)) {
							if (dummystartln[key].getSection() != Double.MIN_VALUE) {
								tlcache.get(dummystartln[key].getSection()).timeline.setNote(key, null);
							}
							dummystartln[key] = null;
							log.add(new DecodeLog(WARNING, "ダミーLN終端位置にノートが存在します。レーン: "
									+ (key + 1) + " - Time(ms):" + tl.getTime()));
						} else {
							boolean hasRealNoteBetween = false;
							for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
								if (e.getKey() >= tl.getSection()) {
									continue;
								}
								final TimeLine tl2 = e.getValue().timeline;
								if (tl2.getSection() == dummystartln[key].getSection()) {
									break;
								}
								if (tl2.existNote(key) && !tl2.getNote(key).isDummy()) {
									hasRealNoteBetween = true;
									break;
								}
							}
							if (hasRealNoteBetween) {
								tlcache.get(dummystartln[key].getSection()).timeline.setNote(key, null);
								dummystartln[key] = null;
								log.add(new DecodeLog(WARNING, "ダミーLN内に通常ノートが存在します。レーン: "
										+ (key + 1) + " - Time(ms):" + tl.getTime()));
							} else {
								for (Map.Entry<Double, TimeLineCache> e : tlcache.descendingMap().entrySet()) {
									if (e.getKey() >= tl.getSection()) {
										continue;
									}
									final TimeLine tl2 = e.getValue().timeline;
									if (tl2.getSection() == dummystartln[key].getSection()) {
										LongNote note = dummystartln[key];
										note.setType(lnmode);
										LongNote noteend = new LongNote(-2);
										noteend.setDummy(true);
										tl.setNote(key, noteend);
										note.setPair(noteend);
										if (lnlist[key] == null) {
											lnlist[key] = new ArrayList<LongNote>();
										}
										lnlist[key].add(note);
										dummystartln[key] = null;
										break;
									} else if (tl2.existNote(key)) {
										log.add(new DecodeLog(WARNING, "ダミーLN内にダミーノートが存在します。レーン: "
												+ (key + 1) + " - Time(ms):" + tl2.getTime()));
										tl2.setNote(key, null);
									}
								}
							}
						}
					} else {
						if (dummystartln[key] == null) {
							LongNote ln = new LongNote(-2);
							ln.setDummy(true);
							ln.setSection(Double.MIN_VALUE);
							dummystartln[key] = ln;
							log.add(new DecodeLog(WARNING, "LN内にダミーLN開始ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						} else {
							if (dummystartln[key].getSection() != Double.MIN_VALUE) {
								tlcache.get(dummystartln[key].getSection()).timeline.setNote(key, null);
							}
							dummystartln[key] = null;
							log.add(new DecodeLog(WARNING, "LN内にダミーLN終端ノートを定義しようとしています : "
									+ (key + 1) + " - Section : " + tl.getSection() + " - Time(ms):" + tl.getTime()));
						}
					}
				}

				// 6. ダミー通常ノーツ (Hx/Ix) を処理する
				if (!ev.dummyKeyNotes.isEmpty()) {
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					final boolean conflict = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection())
							|| startln[key] != null || dummystartln[key] != null;
					final int existingCount = conflict ? 1 : 0;
					for (int i = 0; i < ev.dummyKeyNotes.size(); i++) {
						if (existingCount + i > 0) {
							log.add(new DecodeLog(WARNING, "ダミー通常ノート追加時に衝突が発生しました : " + (key + 1) + ":"
									+ tl.getTime()));
						}
					}
					if (!conflict) {
						NormalNote dn = new NormalNote(-1);
						dn.setDummy(true);
						tl.setNote(key, dn);
					}
				}

				// 7. ダミー地雷ノーツ (Lx/Mx) および 8. ダミーポーションノーツ (Nx/Ox) を処理する
				for (int noteType = 0; noteType < 2; noteType++) {
					for (int ignored : (noteType == 0 ? ev.dummyMineNotes : ev.dummyPotionNotes)) {
						final TimeLine tl = getTimeLine(sectionnum + rate * pos);
						final boolean conflict = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection())
								|| startln[key] != null || dummystartln[key] != null;
						if (!conflict) {
							Note dn = (noteType == 0) ? new MineNote(-1, 0) : new PotionNote(-1, 0);
							dn.setDummy(true);
							tl.setNote(key, dn);
						} else {
							log.add(new DecodeLog(WARNING, (noteType == 0 ? "ダミー地雷ノート" : "ダミーポーションノート")
									+ "追加時に衝突が発生しました : " + (key + 1) + ":" + tl.getTime()));
						}
					}
				}
				for (int noteType = 0; noteType < 0; noteType++)
				for (int rawData : (noteType == 0 ? ev.mineNotes : ev.potionNotes)) {
					int data = rawData;
					final TimeLine tl = getTimeLine(sectionnum + rate * pos);
					boolean insideln = tl.existNote(key) || isInsideExistingLongNote(lnlist[key], tl.getSection());
					if (insideln && noteType == 1) {
						log.add(new DecodeLog(WARNING, "ポーションノート追加時に衝突が発生しました : " + (key + 1) + ":"
								+ tl.getTime()));
						continue;
					}
					if (!insideln) {
						if (base == 62) {
							data = ChartDecoder.parseInt36(ChartDecoder.toBase62(data), 0);
						}
						tl.setNote(key, noteType == 0 ? new MineNote(wavmap[0], data) : new PotionNote(-1, data));
					} else {
						log.add(new DecodeLog(WARNING, "地雷ノート追加時に衝突が発生しました : " + (key + 1) + ":"
								+ tl.getTime()));
					}
				}
			}
		}
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

	private static void addLayeredWavToEndNote(LongNote lnend, int wav) {
		if (wav < 0) {
			return;
		}
		if (lnend.getWav() < 0) {
			lnend.setWav(wav);
			return;
		}
		lnend.addLayeredNote(new NormalNote(wav));
	}

	public double getSectionnum() {
		return sectionnum;
	}

	public double getRate() {
		return rate;
	}

	public static void resolveFreeZones(Section[] sections, BMSModel model, TreeMap<Double, TimeLineCache> tlcache, List<DecodeLog> log) {
		if (model.getMode() == Mode.POPN_9K) {
			return;
		}
		final int[] cassign = (model.getMode() == Mode.BEAT_7K || model.getMode() == Mode.BEAT_14K)
				? CHANNELASSIGN_BEAT7 : CHANNELASSIGN_BEAT5;
		final int p1ScratchLane = cassign[5];
		final int p2ScratchLane = (model.getMode().player == 2) ? cassign[14] : -1;

		final List<double[]> p1Intervals = new ArrayList<>();
		final List<double[]> p2Intervals = new ArrayList<>();
		final double[] openPairStart = new double[] { -1.0, -1.0 };

		for (Section sec : sections) {
			final List<Double> p1PairPos = new ArrayList<>();
			final List<Double> p2PairPos = new ArrayList<>();
			for (String line : sec.channellines) {
				final int channel = ChartDecoder.parseInt36(line.charAt(4), line.charAt(5));
				if (channel == P1_FREEZONE_SINGLE || (sec.legacyFreeZoneEnabled && channel == P1_FREEZONE_LEGACY)) {
					sec.processData(line, (pos, data) -> {
						final double s = sec.sectionnum + sec.rate * pos;
						p1Intervals.add(new double[] { s, s + 0.25 });
					});
				} else if (channel == P2_FREEZONE_SINGLE || (sec.legacyFreeZoneEnabled && channel == P2_FREEZONE_LEGACY)) {
					if (p2ScratchLane >= 0) {
						sec.processData(line, (pos, data) -> {
							final double s = sec.sectionnum + sec.rate * pos;
							p2Intervals.add(new double[] { s, s + 0.25 });
						});
					}
				} else if (channel == P1_FREEZONE_PAIR) {
					sec.processData(line, (pos, data) -> p1PairPos.add(pos));
				} else if (channel == P2_FREEZONE_PAIR) {
					if (p2ScratchLane >= 0) {
						sec.processData(line, (pos, data) -> p2PairPos.add(pos));
					}
				}
			}
			if (!p1PairPos.isEmpty()) {
				Collections.sort(p1PairPos);
				for (double pos : p1PairPos) {
					final double absSec = sec.sectionnum + sec.rate * pos;
					if (openPairStart[0] < 0) {
						openPairStart[0] = absSec;
					} else {
						if (absSec > openPairStart[0]) {
							p1Intervals.add(new double[] { openPairStart[0], absSec });
						}
						openPairStart[0] = -1.0;
					}
				}
			}
			if (!p2PairPos.isEmpty()) {
				Collections.sort(p2PairPos);
				for (double pos : p2PairPos) {
					final double absSec = sec.sectionnum + sec.rate * pos;
					if (openPairStart[1] < 0) {
						openPairStart[1] = absSec;
					} else {
						if (absSec > openPairStart[1]) {
							p2Intervals.add(new double[] { openPairStart[1], absSec });
						}
						openPairStart[1] = -1.0;
					}
				}
			}
		}

		if (openPairStart[0] >= 0) {
			log.add(new DecodeLog(WARNING, "曲の終端までにフリーゾーン終端定義されていないフリーゾーンがあります。lane:" + (p1ScratchLane + 1)));
		}
		if (openPairStart[1] >= 0 && p2ScratchLane >= 0) {
			log.add(new DecodeLog(WARNING, "曲の終端までにフリーゾーン終端定義されていないフリーゾーンがあります。lane:" + (p2ScratchLane + 1)));
		}

		applyMergedFreeZones(tlcache, model, p1ScratchLane, p1Intervals);
		if (p2ScratchLane >= 0) {
			applyMergedFreeZones(tlcache, model, p2ScratchLane, p2Intervals);
		}
	}

	private static void applyMergedFreeZones(TreeMap<Double, TimeLineCache> tlcache, BMSModel model, int lane, List<double[]> intervals) {
		if (intervals.isEmpty()) {
			return;
		}
		intervals.sort((a, b) -> {
			int cmp = Double.compare(a[0], b[0]);
			return cmp != 0 ? cmp : Double.compare(a[1], b[1]);
		});
		final List<double[]> merged = new ArrayList<>();
		double curStart = intervals.get(0)[0];
		double curEnd = intervals.get(0)[1];
		for (int i = 1; i < intervals.size(); i++) {
			final double nextStart = intervals.get(i)[0];
			final double nextEnd = intervals.get(i)[1];
			if (nextStart <= curEnd + 1e-9) {
				if (nextEnd > curEnd) {
					curEnd = nextEnd;
				}
			} else {
				merged.add(new double[] { curStart, curEnd });
				curStart = nextStart;
				curEnd = nextEnd;
			}
		}
		merged.add(new double[] { curStart, curEnd });

		for (double[] range : merged) {
			final TimeLine startTl = getOrCreateTimeLine(tlcache, model, range[0]);
			final TimeLine endTl = getOrCreateTimeLine(tlcache, model, range[1]);
			final List<Note> innerList = new ArrayList<>();
			for (TimeLineCache tlc : tlcache.subMap(startTl.getSection() - 1e-9, true, endTl.getSection() + 1e-9, true).values()) {
				final Note n = tlc.timeline.getNote(lane);
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
			final FreeZoneNote fzStart = new FreeZoneNote();
			final FreeZoneNote fzEnd = new FreeZoneNote();
			startTl.setFreeZone(lane, fzStart);
			endTl.setFreeZone(lane, fzEnd);
			fzStart.setPair(fzEnd);
			fzStart.setInnerNotes(innerList.toArray(new Note[0]));
		}
	}

	public static TimeLine getOrCreateTimeLine(TreeMap<Double, TimeLineCache> tlcache, BMSModel model, double section) {
		final TimeLineCache exact = tlcache.get(section);
		if (exact != null) {
			return exact.timeline;
		}
		final Entry<Double, TimeLineCache> floor = tlcache.floorEntry(section);
		if (floor != null && Math.abs(floor.getKey() - section) < 1e-9) {
			return floor.getValue().timeline;
		}
		final Entry<Double, TimeLineCache> ceil = tlcache.ceilingEntry(section);
		if (ceil != null && Math.abs(ceil.getKey() - section) < 1e-9) {
			return ceil.getValue().timeline;
		}

		final Entry<Double, TimeLineCache> le = tlcache.lowerEntry(section);
		final double scroll = le.getValue().timeline.getScroll();
		final double bpm = le.getValue().timeline.getBPM();
		final double speed = le.getValue().timeline.getSpeed();
		final double time = le.getValue().time + le.getValue().timeline.getMicroStop()
				+ (240000.0 * 1000 * (section - le.getKey())) / bpm;

		final TimeLine tl = new TimeLine(section, (long) time, model.getMode().key);
		tl.setBPM(bpm);
		tl.setScroll(scroll);
		tl.setSpeed(speed);
		tlcache.put(section, new TimeLineCache(time, tl));
		return tl;
	}

	public TimeLine getOrCreateTimeLine(double section) {
		return getTimeLine(section);
	}

	private TimeLine getTimeLine(double section) {
		final TimeLineCache tlc = tlcache.get(section);
		if (tlc != null) {
			return tlc.timeline;
		}
		
		Entry<Double, TimeLineCache> le = tlcache.lowerEntry(section);
		double scroll = le.getValue().timeline.getScroll();
		double bpm = le.getValue().timeline.getBPM();
		double speed = le.getValue().timeline.getSpeed();
		double time = le.getValue().time + le.getValue().timeline.getMicroStop() + (240000.0 * 1000 * (section  - le.getKey())) / bpm;			
		
		TimeLine tl = new TimeLine(section, (long)time, model.getMode().key);
		tl.setBPM(bpm);
		tl.setScroll(scroll);
		tl.setSpeed(speed);
		tlcache.put(section, new TimeLineCache(time, tl));
		return tl;
	}
}
