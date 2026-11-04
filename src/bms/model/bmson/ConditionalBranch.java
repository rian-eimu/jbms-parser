package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionalBranch {
	/**
	 * 条件式文字列（例: "MISS>=100"）
	 */
	public String when;

	/**
	 * デフォルト分岐フラグ
	 */
	@JsonProperty("default")
	public boolean defaultBranch;

	public BarLine[] lines = {};
	public BpmEvent[] bpm_events = {};
	public StopEvent[] stop_events = {};
	public ScrollEvent[] scroll_events = {};
	public ScrollEvent[] speed_events = {};
	public SoundChannel[] sound_channels = {};
	public BGA bga;
	public BNote[] bga_events = {};
	public BNote[] layer_events = {};
	public BNote[] poor_events = {};
	public MineChannel[] mine_channels = {};
	public MineChannel[] key_channels = {};
	public FreeZoneChannel[] free_zone_channels = {};
	public PotionNote[] potion_notes = {};
	public Note[] dummy_notes = {};
	public MineNote[] dummy_mine_notes = {};
	public MineNote[] dummy_potion_notes = {};
}

