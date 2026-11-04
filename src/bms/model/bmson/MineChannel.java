package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MineChannel {
	public String name; // as sound file name
	public MineNote[] notes; // as notes using this sound.
}
