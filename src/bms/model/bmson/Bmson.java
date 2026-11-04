package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)

public class Bmson {
    public String version;        // bmson version
	public BMSInfo info = new BMSInfo(); // as bmson informations.
	public BarLine[] lines = {}; // as line locates.
	public BpmEvent[] bpm_events = {}; // change BPM. value is BPM.
	public StopEvent[] stop_events = {}; // Stop flow. value is StopTime.
	public ScrollEvent[] scroll_events = {}; // Stop flow. value is StopTime.
	public ScrollEvent[] speed_events = {}; // ハイスピード変化定義
	public SoundChannel[] sound_channels = {}; // as Note data.
	public BGA bga = new BGA(); // as BGA(movie) data.
	public MineChannel[] mine_channels = {}; // as Mine Note data.
	public MineChannel[] key_channels = {}; // as Key Note data.
	public FreeZoneChannel[] free_zone_channels = {}; // フリーゾーン定義
	public PotionNote[] potion_notes = {}; // ポーションノーツ定義
	public Note[] dummy_notes = {}; // ダミー通常ノーツ / ダミーLN定義
	public MineNote[] dummy_mine_notes = {}; // ダミー地雷定義
	public MineNote[] dummy_potion_notes = {}; // ダミーポーション定義
	public ConditionalBlock[] conditional_blocks = {}; // 動的条件分岐定義

	public BpmEvent[] getBpmNotes() {
		return bpm_events;
	}
	
	public void setBpmNotes(BpmEvent[] bpm_events) {
		this.bpm_events = bpm_events;
	}
	
	public StopEvent[] getStopNotes() {
		return stop_events;
	}

	public void setStopNotes(StopEvent[] stop_events) {
		this.stop_events = stop_events;
	}
	
	public SoundChannel[] getSoundChannel() {
		return sound_channels;
	}

	public void setSoundChannel(SoundChannel[] sound_channels) {
		this.sound_channels = sound_channels;
	}
}
