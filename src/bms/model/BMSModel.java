package bms.model;

import java.util.HashMap;
import java.util.Map;

/**
 * BMSモデル
 * 
 * @author exch
 */
public class BMSModel implements Comparable<BMSModel> {

	/**
	 * プレイヤー数
	 */
	private int player;
	/**
	 * 使用するキー数
	 */
	private Mode mode;
	/**
	 * タイトル名
	 */
	private String title = "";
	/**
	 * サブタイトル名
	 */
	private String subTitle = "";
	/**
	 * ジャンル名
	 */
	private String genre = "";
	/**
	 * アーティスト
	 */
	private String artist = "";
	/**
	 * サブアーティスト
	 */
	private String subartist = "";

	/**
	 * バナー
	 */
	private String banner = "";
	/**
	 * ステージ画像
	 */
	private String stagefile = "";
	private String backbmp = "";
	private String preview = "";
	/**
	 * 標準BPM
	 */
	private double bpm;
	/**
	 * 表記レベル
	 */
	private String playlevel = "";
	/**
	 * 表記ランク(0:beginner, 1:normal, 2:hyper, 3:another, 4:insane)
	 */
	private int difficulty = 0;
	/**
	 * 判定ランク
	 */
	private int judgerank = 2;
	/**
	 * 判定ランクのタイプ
	 */
	private JudgeRankType judgerankType = JudgeRankType.BMS_RANK;
	/**
	 * TOTAL値
	 */
	private double total = 100;
	/**
	 * TOTALのタイプ
	 */
	private TotalType totalType = TotalType.BMSON;
	/**
	 * 標準ボリューム
	 */
	private int volwav;
	/**
	 * MD5値
	 */
	private String md5 = "";
	/**
	 * SHA256値
	 */
	private String sha256 = "";
	/**
	 * WAV定義のIDとファイル名のマップ
	 */
	private String[] wavmap = new String[0];
	/**
	 * BGA定義のIDとファイル名のマップ
	 */
	private String[] bgamap = new String[0];
	/**
	 * 進数指定
	 */
	private int base = 36;

	private int lnmode = LongNote.TYPE_UNDEFINED;

	private int lnobj = -1;
	private int cnobj = -1;
	private int hcnobj = -1;
	private int hlnobj = -1;

	/**
	 * Whether this model is decoded by osu decoder or not
	 */
	private boolean fromOSU;

	public static final int LNTYPE_LONGNOTE = 0;
	public static final int LNTYPE_CHARGENOTE = 1;
	public static final int LNTYPE_HELLCHARGENOTE = 2;
	public static final int LNTYPE_HELLLONGNOTE = 3;

	/**
	 * 時間とTimeLineのマッピング
	 */
	private TimeLine[] timelines = new TimeLine[0];

	private ChartInformation info;

	private Map<String, String> values = new HashMap<>();

	public BMSModel() {
	}

	public int getPlayer() {
		return player;
	}

	public void setPlayer(int player) {
		this.player = player;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		if (title == null) {
			this.title = "";
			return;
		}
		this.title = title;
	}

	public String getSubTitle() {
		return subTitle;
	}

	public void setSubTitle(String subTitle) {
		if (subTitle == null) {
			this.subTitle = "";
			return;
		}
		this.subTitle = subTitle;
	}

	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		if (genre == null) {
			this.genre = "";
			return;
		}
		this.genre = genre;
	}

	public String getArtist() {
		return artist;
	}

	public void setArtist(String artist) {
		if (artist == null) {
			this.artist = "";
			return;
		}
		this.artist = artist;
	}

	public String getSubArtist() {
		return subartist;
	}

	public void setSubArtist(String artist) {
		if (artist == null) {
			this.subartist = "";
			return;
		}
		this.subartist = artist;
	}

	public void setBanner(String banner) {
		if (banner == null) {
			this.banner = "";
			return;
		}
		this.banner = banner;
	}

	public String getBanner() {
		return banner;
	}

	public double getBpm() {
		return bpm;
	}

	public void setBpm(double bpm) {
		;
		this.bpm = bpm;
	}

	public String getPlaylevel() {
		return playlevel;
	}

	public void setPlaylevel(String playlevel) {
		this.playlevel = playlevel;
	}

	public int getJudgerank() {
		return judgerank;
	}

	public void setJudgerank(int judgerank) {
		this.judgerank = judgerank;
	}

	public double getTotal() {
		return total;
	}

	public void setTotal(double total) {
		this.total = total;
	}

	public int getVolwav() {
		return volwav;
	}

	public void setVolwav(int volwav) {
		this.volwav = volwav;
	}

	public double getMinBPM() {
		double bpm = this.getBpm();
		for (TimeLine time : timelines) {
			final double d = time.getBPM();
			bpm = (bpm <= d) ? bpm : d;
		}
		return bpm;
	}

	public double getMaxBPM() {
		double bpm = this.getBpm();
		for (TimeLine time : timelines) {
			final double d = time.getBPM();
			bpm = (bpm >= d) ? bpm : d;
		}
		return bpm;
	}

	public void setAllTimeLine(TimeLine[] timelines) {
		this.timelines = timelines;
	}

	public TimeLine[] getAllTimeLines() {
		return timelines;
	}

	public long[] getAllTimes() {
		TimeLine[] times = getAllTimeLines();
		long[] result = new long[times.length];
		for (int i = 0; i < times.length; i++) {
			result[i] = times[i].getTime();
		}
		return result;
	}

	public int getLastTime() {
		return (int) getLastMilliTime();
	}

	public long getLastMilliTime() {
		final int keys = mode.key;
		for (int i = timelines.length - 1;i >= 0;i--) {
			final TimeLine tl = timelines[i];
			for (int lane = 0; lane < keys; lane++) {
				if (tl.existNote(lane) || tl.existFreeZone(lane) || tl.getHiddenNote(lane) != null
						|| tl.getBackGroundNotes().length > 0 || tl.getBGA() != -1
						|| tl.getLayer() != -1) {
					return tl.getMilliTime();
				}
			}
		}
		return 0;
	}

	public int getLastNoteTime() {
		return (int) getLastNoteMilliTime();
	}

	public long getLastNoteMilliTime() {
		final int keys = mode.key;
		for (int i = timelines.length - 1;i >= 0;i--) {
			final TimeLine tl = timelines[i];
			for (int lane = 0; lane < keys; lane++) {
				if (tl.existNote(lane) || tl.existFreeZone(lane)) {
					return tl.getMilliTime();
				}
			}
		}
		return 0;
	}

	public int getDifficulty() {
		return difficulty;
	}

	public void setDifficulty(int difficulty) {
		this.difficulty = difficulty;
	}

	public int compareTo(BMSModel model) {
		return this.title.compareTo(model.title);
	}

	public String getFullTitle() {
		return title + (subTitle != null && subTitle.length() > 0 ? " " + subTitle : "");
	}

	public String getFullArtist() {
		return artist + (subartist != null && subartist.length() > 0 ? " " + subartist : "");
	}

	public void setMD5(String hash) {
		this.md5 = hash;
	}

	public String getMD5() {
		return md5;
	}

	public String getSHA256() {
		return sha256;
	}

	public void setSHA256(String sha256) {
		this.sha256 = sha256;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
		for(TimeLine tl : timelines) {
			tl.setLaneCount(mode.key);
		}
	}

	public Mode getMode() {
		return mode;
	}

	public String[] getWavList() {
		return wavmap;
	}

	public void setWavList(String[] wavmap) {
		this.wavmap = wavmap;
	}

	public String[] getBgaList() {
		return bgamap;
	}

	public void setBgaList(String[] bgamap) {
		this.bgamap = bgamap;
	}

	public ChartInformation getChartInformation() {
		return info;
	}

	public void setChartInformation(ChartInformation info) {
		this.info = info;
	}
	
	public int[] getRandom() {
		return info != null ? info.selectedRandoms : null;
	}
	
	public String getPath() {
		if(info == null) {
			return null;
		}
		if(info.source != null) {
			return info.source.location();
		}
		return info.path != null ? info.path.toString() : null;
	}
	
	public int getLntype() {
		return info != null ? info.lntype : LNTYPE_LONGNOTE;
	}

	public String getStagefile() {
		return stagefile;
	}

	public void setStagefile(String stagefile) {
		if (stagefile == null) {
			this.stagefile = "";
			return;
		}
		this.stagefile = stagefile;
	}

	public String getBackbmp() {
		return backbmp;
	}

	public void setBackbmp(String backbmp) {
		if (backbmp == null) {
			this.backbmp = "";
			return;
		}
		this.backbmp = backbmp;
	}

	public int getTotalNotes() {
		return BMSModelUtils.getTotalNotes(this);
	}

	public boolean isFromOSU() {
		return fromOSU;
	}

	public void setFromOSU(boolean fromOSU) {
		this.fromOSU = fromOSU;
	}

	public boolean containsUndefinedLongNote() {
		final int keys = mode.key;
		for (TimeLine tl : timelines) {
			for (int i = 0; i < keys; i++) {
				if (tl.getNote(i) != null && tl.getNote(i) instanceof LongNote && !tl.getNote(i).isDummy()
						&& ((LongNote) tl.getNote(i)).getType() == LongNote.TYPE_UNDEFINED) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean containsLongNote() {
		final int keys = mode.key;
		for (TimeLine tl : timelines) {
			for (int i = 0; i < keys; i++) {
				if (tl.getNote(i) instanceof LongNote && !tl.getNote(i).isDummy()) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean containsMineNote() {
		final int keys = mode.key;
		for (TimeLine tl : timelines) {
			for (int i = 0; i < keys; i++) {
				if (tl.getNote(i) instanceof MineNote && !tl.getNote(i).isDummy()) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean containsFreeZone() {
		final int keys = mode.key;
		for (TimeLine tl : timelines) {
			for (int i = 0; i < keys; i++) {
				if (tl.existFreeZone(i)) {
					return true;
				}
			}
		}
		return false;
	}

	public String getPreview() {
		return preview;
	}

	public void setPreview(String preview) {
		this.preview = preview;
	}

	public EventLane getEventLane() {
		return new EventLane(this);
	}	

	public Lane[] getLanes() {
		Lane[] lanes = new Lane[mode.key];
		for(int i = 0;i < lanes.length;i++) {
			lanes[i] = new Lane(this, i);
		}
		return lanes;
	}

	public int getLnobj() {
		return lnobj;
	}

	public void setLnobj(int lnobj) {
		this.lnobj = lnobj;
	}

	public int getCnobj() {
		return cnobj;
	}

	public void setCnobj(int cnobj) {
		this.cnobj = cnobj;
	}

	public int getHcnobj() {
		return hcnobj;
	}

	public void setHcnobj(int hcnobj) {
		this.hcnobj = hcnobj;
	}

	public int getHlnobj() {
		return hlnobj;
	}

	public void setHlnobj(int hlnobj) {
		this.hlnobj = hlnobj;
	}

	public int getLnmode() {
		return lnmode;
	}

	public void setLnmode(int lnmode) {
		this.lnmode = lnmode;
	}

	public Map<String, String> getValues() {
		return values;
	}

	public String toChartString() {
		StringBuilder sb = new StringBuilder();
		sb.append("JUDGERANK:" + judgerank + "\n");
		sb.append("TOTAL:" + total + "\n");
		if(lnmode != 0) {
			sb.append("LNMODE:" + lnmode + "\n");			
		}
		double nowbpm = -Double.MIN_VALUE;
		double nowspeed = 1.0;
		StringBuilder tlsb = new StringBuilder();
		for(TimeLine tl : timelines) {
			tlsb.setLength(0);
			tlsb.append(tl.getTime() + ":");
			boolean write = false;			
			if(nowbpm != tl.getBPM()) {
				nowbpm = tl.getBPM();
				tlsb.append("B(" + nowbpm + ")");
				write = true;
			}
			if(tl.getStop() != 0) {
				tlsb.append("S(" + tl.getStop() + ")");
				write = true;
			}
			if(tl.hasSpeedObj()) {
				nowspeed = tl.getSpeed();
				tlsb.append("SP(" + nowspeed + ")");
				write = true;
			}
			if(tl.getSectionLine()) {
				tlsb.append("L");
				write = true;
			}

			tlsb.append("[");
			for(int lane = 0;lane < mode.key;lane++) {
				Note n = tl.getNote(lane);
				FreeZoneNote fz = tl.getFreeZone(lane);
				boolean hasFzStart = fz != null && !fz.isEnd();
				if(hasFzStart) {
					tlsb.append("F" + fz.getMilliDuration());
					write = true;
				}
				if(n instanceof NormalNote) {
					tlsb.append("1");
					write = true;
				} else if(n instanceof LongNote) {
					LongNote ln = (LongNote)n;
					if(!ln.isEnd()) {
						final char[] lnchars = {'l','L','C','H','h'};
						tlsb.append(lnchars[ln.getType()] + ln.getMilliDuration());
						write = true;
					}
				} else if(n instanceof MineNote) {
					tlsb.append("m" + ((MineNote)n).getDamage());
					write = true;					
				} else {
					if (!hasFzStart) {
						tlsb.append("0");
					}
				}
				if(lane < mode.key - 1) {
					tlsb.append(",");					
				}
			}
			tlsb.append("]\n");
			
			if(write) {
				sb.append(tlsb);
			}
		}
		return sb.toString();
	}

	public JudgeRankType getJudgerankType() {
		return judgerankType;
	}

	public void setJudgerankType(JudgeRankType judgerankType) {
		this.judgerankType = judgerankType;
	}

	public TotalType getTotalType() {
		return totalType;
	}

	public void setTotalType(TotalType totalType) {
		this.totalType = totalType;
	}

	public enum JudgeRankType {
		BMS_RANK, BMS_DEFEXRANK, BMSON_JUDGERANK;
	}

	public enum TotalType {
		BMS, BMSON;
	}

	/**
	 * 動的条件分岐ブロック定義
	 */
	private ConditionalBlock[] conditionalBlocks = new ConditionalBlock[0];

	/**
	 * 各動的条件分岐ブロックの評価位置に対応するTimeLine
	 */
	private TimeLine[] conditionalEvalTimeLines = new TimeLine[0];

	/**
	 * 動的条件分岐のTimeLine再構築ビルダー
	 */
	private ConditionalBlock.ChartBuilder conditionalChartBuilder;

	/**
	 * 動的条件分岐の全枝で参照されるWAV ID一覧
	 */
	private int[] conditionalWavIds = new int[0];

	/**
	 * 動的条件分岐の枝内に地雷ノーツが含まれるかどうか
	 */
	private boolean conditionalContainsMineNote;

	/**
	 * 動的条件分岐の枝内にフリーゾーンが含まれるかどうか
	 */
	private boolean conditionalContainsFreeZone;

	/**
	 * 動的条件分岐の全枝で事前ロード対象となるノーツ一覧（スライス範囲情報を含む）
	 */
	private Note[] conditionalPreloadNotes = new Note[0];

	public boolean hasConditional() {
		return conditionalBlocks != null && conditionalBlocks.length > 0;
	}

	public ConditionalBlock[] getConditionalBlocks() {
		return conditionalBlocks;
	}

	public void setConditionalBlocks(ConditionalBlock[] conditionalBlocks) {
		this.conditionalBlocks = conditionalBlocks != null ? conditionalBlocks : new ConditionalBlock[0];
	}

	public TimeLine[] getConditionalEvalTimeLines() {
		return conditionalEvalTimeLines;
	}

	public void setConditionalEvalTimeLines(TimeLine[] conditionalEvalTimeLines) {
		this.conditionalEvalTimeLines = conditionalEvalTimeLines != null ? conditionalEvalTimeLines : new TimeLine[0];
	}

	public ConditionalBlock.ChartBuilder getConditionalChartBuilder() {
		return conditionalChartBuilder;
	}

	public void setConditionalChartBuilder(ConditionalBlock.ChartBuilder conditionalChartBuilder) {
		this.conditionalChartBuilder = conditionalChartBuilder;
	}

	public int[] getConditionalWavIds() {
		return conditionalWavIds;
	}

	public void setConditionalWavIds(int[] conditionalWavIds) {
		this.conditionalWavIds = conditionalWavIds != null ? conditionalWavIds : new int[0];
	}

	public boolean isConditionalContainsMineNote() {
		return conditionalContainsMineNote;
	}

	public void setConditionalContainsMineNote(boolean conditionalContainsMineNote) {
		this.conditionalContainsMineNote = conditionalContainsMineNote;
	}

	public boolean isConditionalContainsFreeZone() {
		return conditionalContainsFreeZone;
	}

	public void setConditionalContainsFreeZone(boolean conditionalContainsFreeZone) {
		this.conditionalContainsFreeZone = conditionalContainsFreeZone;
	}

	public Note[] getConditionalPreloadNotes() {
		return conditionalPreloadNotes;
	}

	public void setConditionalPreloadNotes(Note[] conditionalPreloadNotes) {
		this.conditionalPreloadNotes = conditionalPreloadNotes != null ? conditionalPreloadNotes : new Note[0];
	}

	public int getBase() {
		return base;
	}

	public void setBase(int base) {
		if (base == 62) {
			this.base = base;
		} else {
			this.base = 36;
		}
	}
}
