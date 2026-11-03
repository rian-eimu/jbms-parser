package bms.model;

/**
 * フリーゾーン（スクラッチ自由回転区間）ノート
 */
public class FreeZoneNote extends Note {

	public static final FreeZoneNote[] EMPTYARRAY = new FreeZoneNote[0];

	/**
	 * フリーゾーン終端かどうか
	 */
	private boolean end;
	/**
	 * ペアになっているフリーゾーンノート
	 */
	private FreeZoneNote pair;
	/**
	 * フリーゾーン区間内に内包される皿ノート一覧（NormalNote または LongNote 始点）
	 */
	private Note[] innerNotes = Note.EMPTYARRAY;
	/**
	 * フリーゾーン区間内で1回以上スクラッチ入力があったかどうか
	 */
	private boolean scratched;

	public FreeZoneNote() {
		// フリーゾーン自体は区間マーカーとして無音扱い
		this.setWav(-2);
	}

	public void setPair(FreeZoneNote pair) {
		pair.pair = this;
		this.pair = pair;

		pair.end = pair.getSection() > this.getSection();
		this.end = !pair.end;
	}

	public FreeZoneNote getPair() {
		return pair;
	}

	public boolean isEnd() {
		return end;
	}

	public Note[] getInnerNotes() {
		return innerNotes;
	}

	public void setInnerNotes(Note[] innerNotes) {
		this.innerNotes = innerNotes != null ? innerNotes : Note.EMPTYARRAY;
		if (pair != null && pair.innerNotes != this.innerNotes) {
			pair.innerNotes = this.innerNotes;
		}
	}

	public boolean isScratched() {
		return scratched;
	}

	public void setScratched(boolean scratched) {
		this.scratched = scratched;
		if (pair != null && pair.scratched != scratched) {
			pair.scratched = scratched;
		}
	}

	@Override
	public long getMicroDuration() {
		if (pair != null) {
			return Math.abs(pair.getMicroTime() - this.getMicroTime());
		}
		return super.getMicroDuration();
	}

	@Override
	public long getMilliDuration() {
		return getMicroDuration() / 1000;
	}

	@Override
	public Object clone() {
		return clone(true);
	}

	private Object clone(boolean copypair) {
		FreeZoneNote fz = (FreeZoneNote) super.clone();
		if (copypair && pair != null) {
			fz.setPair((FreeZoneNote) pair.clone(false));
		}
		return fz;
	}
}
