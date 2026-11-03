package bms.model;

import java.io.IOException;
import java.io.InputStream;

/**
 * 譜面データの入力元を表すインターフェース
 */
public interface ChartSource {

	/**
	 * 譜面データのパス文字列を返す
	 *
	 * @return パス文字列
	 */
	String location();

	/**
	 * 譜面データの入力ストリームを開く
	 *
	 * @return 入力ストリーム
	 * @throws IOException 入出力例外
	 */
	InputStream openStream() throws IOException;

	/**
	 * 譜面データの最終更新日時(エポックミリ秒)を返す。取得できない場合は -1L を返す。
	 *
	 * @return 最終更新日時(ms) または -1L
	 */
	default long lastModifiedTimeMillis() {
		return -1L;
	}
}

