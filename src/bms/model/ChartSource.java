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
}

