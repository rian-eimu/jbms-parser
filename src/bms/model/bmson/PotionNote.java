package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PotionNote extends BMSONObject {
	/**
	 * レーン番号
	 */
	public int x;

	/**
	 * ゲージ回復量（%）
	 */
	public double recovery;
}

