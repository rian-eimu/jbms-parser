package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeZoneChannel {
	/**
	 * 区間開始時の音声ファイル名（任意）
	 */
	public String name;

	/**
	 * フリーゾーン区間定義配列
	 */
	public FreeZoneNote[] notes = {};
}

