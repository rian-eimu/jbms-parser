package bms.model.bmson;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionalBlock extends BMSONObject {
	/**
	 * 分岐定義の配列（上から順に評価）
	 */
	public ConditionalBranch[] branches = {};
}

