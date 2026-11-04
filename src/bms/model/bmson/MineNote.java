package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MineNote extends BMSONObject {
	/**
	 * レーン番号
	 */
	public int x;
	
	public double damage;

}
