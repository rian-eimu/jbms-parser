package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeZoneNote extends BMSONObject {
	/**
	 * 皿レーン番号（beat-5k / beat-7k は 8、beat-10k / beat-14k の2P皿は 16）
	 */
	public int x;

	/**
	 * 区間長（pulse）。省略または 0 以下の場合は四分音符1個分
	 */
	public int l;
}

