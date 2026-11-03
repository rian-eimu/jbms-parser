package bms.model;

/**
 * ポーションノーツ
 */
public class PotionNote extends Note {

	/**
	 * ポーションの回復量(%)
	 */
	private double recovery;

	public PotionNote(int wav, double recovery) {
		this.setWav(wav);
		this.setRecovery(recovery);
	}

	/**
	 * ポーションノーツの回復量を取得する
	 * @return ポーションノーツの回復量(%)
	 */
	public double getRecovery() {
		return recovery;
	}

	/**
	 * ポーションノーツの回復量を設定する
	 * @param recovery ポーションノーツの回復量(%)
	 */
	public void setRecovery(double recovery) {
		this.recovery = recovery;
	}

}

