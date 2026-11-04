package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScrollEvent extends BMSONObject {
	/**
	 * スクロールスピード倍率
	 */
	@JsonAlias({ "speed", "value" })
	public double rate = 1.0;

}
