package bms.model;

import java.util.*;
import java.util.function.ToDoubleFunction;

import bms.model.ChartDecoder.TimeLineCache;

import static bms.model.DecodeLog.State.*;

/**
 * 動的条件分岐 (#CONDITIONAL) のブロック定義および譜面再構築モデル
 */
public class ConditionalBlock {

	/**
	 * 条件変数
	 */
	public enum Variable {
		SCORE,
		RATE,
		COMBO,
		MAXCOMBO,
		PGREAT,
		GREAT,
		GOOD,
		BAD,
		POOR,
		MISS,
		GAUGE,
		LAMP,
		AUTO
	}

	/**
	 * 比較演算子
	 */
	public enum Operator {
		GE(">="),
		LE("<="),
		EQ("=="),
		NE("!="),
		GT(">"),
		LT("<");

		private static final double EPS = 1e-6;
		public final String symbol;

		Operator(String symbol) {
			this.symbol = symbol;
		}

		public boolean evaluate(double lhs, double rhs) {
			return switch (this) {
				case GT -> lhs > rhs + EPS;
				case GE -> lhs >= rhs - EPS;
				case LT -> lhs < rhs - EPS;
				case LE -> lhs <= rhs + EPS;
				case EQ -> Math.abs(lhs - rhs) <= EPS;
				case NE -> Math.abs(lhs - rhs) > EPS;
			};
		}
	}

	/**
	 * 単一条件式 (<変数> <演算子> <数値>)
	 */
	public static class Condition {
		private final Variable variable;
		private final Operator operator;
		private final double threshold;

		public Condition(Variable variable, Operator operator, double threshold) {
			this.variable = variable;
			this.operator = operator;
			this.threshold = threshold;
		}

		public Variable getVariable() {
			return variable;
		}

		public Operator getOperator() {
			return operator;
		}

		public double getThreshold() {
			return threshold;
		}

		public boolean evaluate(ToDoubleFunction<Variable> provider) {
			return operator.evaluate(provider.applyAsDouble(variable), threshold);
		}

		/**
		 * 条件式文字列をパースする
		 *
		 * @param expr 条件式文字列 (例: "MISS>=100", "GAUGE < 30")
		 * @return パースされたCondition。不正な場合はnull
		 */
		public static Condition parse(String expr) {
			if (expr == null) {
				return null;
			}
			final String trimmed = expr.trim();
			if (trimmed.isEmpty()) {
				return null;
			}
			// 2文字演算子を先に検索するため、Operator定義順 (GE, LE, EQ, NE, GT, LT) で走査
			for (Operator op : Operator.values()) {
				final int idx = trimmed.indexOf(op.symbol);
				if (idx > 0) {
					final String left = trimmed.substring(0, idx).trim();
					final String right = trimmed.substring(idx + op.symbol.length()).trim();
					if (left.isEmpty() || right.isEmpty()) {
						return null;
					}
					// 複合式や余分な演算子が含まれていないか確認
					try {
						final Variable var = Variable.valueOf(left.toUpperCase(Locale.ROOT));
						final double val = Double.parseDouble(right);
						if (Double.isNaN(val) || Double.isInfinite(val)) {
							return null;
						}
						return new Condition(var, op, val);
					} catch (IllegalArgumentException e) {
						return null;
					}
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return variable.name() + operator.symbol + threshold;
		}
	}

	/**
	 * 分岐枝 (#WHEN / #ELSEWHEN / #DEFAULT)
	 */
	public static class Branch {
		private final Condition condition;

		public Branch(Condition condition) {
			this.condition = condition;
		}

		public Condition getCondition() {
			return condition;
		}

		public boolean isDefault() {
			return condition == null;
		}
	}

	private final int blockIndex;
	private final int evalBar;
	private final double evalFraction;
	private final List<Branch> branches;

	public ConditionalBlock(int blockIndex, int evalBar, double evalFraction, List<Branch> branches) {
		this.blockIndex = blockIndex;
		this.evalBar = evalBar;
		this.evalFraction = evalFraction;
		this.branches = Collections.unmodifiableList(new ArrayList<>(branches));
	}

	public int getBlockIndex() {
		return blockIndex;
	}

	public int getEvalBar() {
		return evalBar;
	}

	public double getEvalFraction() {
		return evalFraction;
	}

	public double getEvalPosition() {
		return evalBar + evalFraction;
	}

	public List<Branch> getBranches() {
		return branches;
	}

	public int getDefaultBranchIndex() {
		return branches.size() - 1;
	}

	/**
	 * 現在のライブ値に基づいて条件を上から評価し、採択された枝のインデックスを返す
	 */
	public int evaluate(ToDoubleFunction<Variable> provider) {
		for (int i = 0; i < branches.size(); i++) {
			final Branch b = branches.get(i);
			if (b.isDefault() || b.getCondition().evaluate(provider)) {
				return i;
			}
		}
		return getDefaultBranchIndex();
	}

	/**
	 * 原文順で保持されるチャンネル定義行エントリ
	 */
	public static class ChannelLineEntry {
		public final int blockIndex;
		public final int branchIndex;
		public final int barIndex;
		public final String line;

		public ChannelLineEntry(int blockIndex, int branchIndex, int barIndex, String line) {
			this.blockIndex = blockIndex;
			this.branchIndex = branchIndex;
			this.barIndex = barIndex;
			this.line = line;
		}
	}

	/**
	 * 採択枝配列から Section および TimeLine を再構築するビルダー
	 */
	public static class ChartBuilder {
		private final List<ChannelLineEntry> entries;
		private final ConditionalBlock[] blocks;
		private final Map<Integer, Double> bpmtable;
		private final Map<Integer, Double> stoptable;
		private final Map<Integer, Double> scrolltable;
		private final Map<Integer, Double> speedtable;
		private final int[] wm;
		private final int[] bm;
		private final Mode resolvedMode;
		private final int maxBarIndex;

		public ChartBuilder(List<ChannelLineEntry> entries, ConditionalBlock[] blocks,
				Map<Integer, Double> bpmtable, Map<Integer, Double> stoptable,
				Map<Integer, Double> scrolltable, Map<Integer, Double> speedtable,
				int[] wm, int[] bm, Mode resolvedMode, int maxBarIndex) {
			this.entries = new ArrayList<>(entries);
			this.blocks = blocks.clone();
			this.bpmtable = new TreeMap<>(bpmtable);
			this.stoptable = new TreeMap<>(stoptable);
			this.scrolltable = new TreeMap<>(scrolltable);
			this.speedtable = new TreeMap<>(speedtable);
			this.wm = wm.clone();
			this.bm = bm.clone();
			this.resolvedMode = resolvedMode;
			int maxBar = maxBarIndex;
			for (ConditionalBlock block : blocks) {
				if (block.getEvalBar() > maxBar) {
					maxBar = block.getEvalBar();
				}
			}
			this.maxBarIndex = maxBar;
		}

		public ConditionalBlock[] getBlocks() {
			return blocks;
		}

		public Mode getResolvedMode() {
			return resolvedMode;
		}

		/**
		 * 指定された採択枝インデックス配列で BMSModel の TimeLine 群を構築する
		 *
		 * @param model 対象BMSModel
		 * @param selectedBranches 各ブロックで選択された枝インデックス（nullの場合はすべてDEFAULT枝）
		 * @param log デコードログ出力先
		 */
		@SuppressWarnings("unchecked")
		public void buildTimeLines(BMSModel model, int[] selectedBranches, List<DecodeLog> log) {
			final List<String>[] barLines = new List[maxBarIndex + 1];
			for (ChannelLineEntry entry : entries) {
				if (entry.blockIndex == -1) {
					List<String> l = barLines[entry.barIndex];
					if (l == null) {
						l = barLines[entry.barIndex] = new ArrayList<>();
					}
					l.add(entry.line);
				} else if (entry.blockIndex >= 0 && entry.blockIndex < blocks.length) {
					final ConditionalBlock block = blocks[entry.blockIndex];
					final int targetBranch = (selectedBranches != null
							&& entry.blockIndex < selectedBranches.length
							&& selectedBranches[entry.blockIndex] >= 0
							&& selectedBranches[entry.blockIndex] < block.getBranches().size())
									? selectedBranches[entry.blockIndex]
									: block.getDefaultBranchIndex();
					if (entry.branchIndex == targetBranch) {
						List<String> l = barLines[entry.barIndex];
						if (l == null) {
							l = barLines[entry.barIndex] = new ArrayList<>();
						}
						l.add(entry.line);
					}
				}
			}

			model.setMode(resolvedMode);

			Section prev = null;
			final Section[] sections = new Section[maxBarIndex + 1];
			for (int i = 0; i <= maxBarIndex; i++) {
				sections[i] = new Section(model, prev, barLines[i] != null ? barLines[i] : Collections.emptyList(),
						bpmtable, stoptable, scrolltable, speedtable, log);
				prev = sections[i];
			}

			final TreeMap<Double, TimeLineCache> timelines = new TreeMap<>();
			final List<LongNote>[] lnlist = new List[model.getMode().key];
			final LongNote[] lnendstatus = new LongNote[model.getMode().key];
			final TimeLine basetl = new TimeLine(0, 0, model.getMode().key);
			basetl.setBPM(model.getBpm());
			if (bm[0] != -2) {
				final Layer.Sequence[] poors = new Layer.Sequence[] {
						new Layer.Sequence(0, bm[0]),
						new Layer.Sequence(500)
				};
				basetl.setEventlayer(new Layer[] {
						new Layer(new Layer.Event(Layer.EventType.MISS, 1), new Layer.Sequence[][] { poors }) });
			}
			timelines.put(0.0, new TimeLineCache(0.0, basetl));
			for (Section section : sections) {
				section.makeTimeLines(wm, bm, timelines, lnlist, lnendstatus);
			}

			// 各CONDITIONALブロックの評価位置に対応するTimeLineを取得（未生成なら生成）
			final TimeLine[] evalTimeLines = new TimeLine[blocks.length];
			for (int i = 0; i < blocks.length; i++) {
				final ConditionalBlock block = blocks[i];
				final Section sec = sections[block.getEvalBar()];
				final double absSection = sec.getSectionnum() + sec.getRate() * block.getEvalFraction();
				evalTimeLines[i] = sec.getOrCreateTimeLine(absSection);
			}

			final TimeLine[] tl = new TimeLine[timelines.size()];
			int tlcount = 0;
			for (TimeLineCache tlc : timelines.values()) {
				tl[tlcount++] = tlc.timeline;
			}
			model.setAllTimeLine(tl);
			model.setConditionalEvalTimeLines(evalTimeLines);

			for (int i = 0; i < lnendstatus.length; i++) {
				if (lnendstatus[i] != null) {
					log.add(new DecodeLog(WARNING, "曲の終端までにLN終端定義されていないLNがあります。lane:" + (i + 1)));
					if (lnendstatus[i].getSection() != Double.MIN_VALUE) {
						TimeLineCache tlc = timelines.get(lnendstatus[i].getSection());
						if (tlc != null) {
							tlc.timeline.setNote(i, null);
						}
					}
				}
			}
		}

		/**
		 * 現在のBMSModelのメタデータ・設定を引き継いだ新しいBMSModelを生成し、指定された枝でTimeLineを構築する
		 */
		public BMSModel createBranchedModel(BMSModel baseModel, int[] selectedBranches) {
			BMSModel branched = new BMSModel();
			branched.setPlayer(baseModel.getPlayer());
			branched.setTitle(baseModel.getTitle());
			branched.setSubTitle(baseModel.getSubTitle());
			branched.setGenre(baseModel.getGenre());
			branched.setArtist(baseModel.getArtist());
			branched.setSubArtist(baseModel.getSubArtist());
			branched.setBanner(baseModel.getBanner());
			branched.setStagefile(baseModel.getStagefile());
			branched.setBackbmp(baseModel.getBackbmp());
			branched.setPreview(baseModel.getPreview());
			branched.setBpm(baseModel.getBpm());
			branched.setPlaylevel(baseModel.getPlaylevel());
			branched.setDifficulty(baseModel.getDifficulty());
			branched.setJudgerank(baseModel.getJudgerank());
			branched.setJudgerankType(baseModel.getJudgerankType());
			branched.setTotal(baseModel.getTotal());
			branched.setTotalType(baseModel.getTotalType());
			branched.setVolwav(baseModel.getVolwav());
			branched.setMD5(baseModel.getMD5());
			branched.setSHA256(baseModel.getSHA256());
			branched.setWavList(baseModel.getWavList());
			branched.setBgaList(baseModel.getBgaList());
			branched.setBase(baseModel.getBase());
			branched.setLnmode(baseModel.getLnmode());
			branched.setLnobj(baseModel.getLnobj());
			branched.setCnobj(baseModel.getCnobj());
			branched.setHcnobj(baseModel.getHcnobj());
			branched.setHlnobj(baseModel.getHlnobj());
			branched.setFromOSU(baseModel.isFromOSU());
			branched.setChartInformation(baseModel.getChartInformation());
			branched.getValues().putAll(baseModel.getValues());
			branched.setConditionalBlocks(this.blocks);
			branched.setConditionalChartBuilder(this);
			branched.setConditionalWavIds(baseModel.getConditionalWavIds());
			branched.setConditionalContainsMineNote(baseModel.isConditionalContainsMineNote());

			List<DecodeLog> dummyLog = new ArrayList<>();
			buildTimeLines(branched, selectedBranches, dummyLog);
			return branched;
		}
	}
}

