package bms.model;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/**
 * バイト配列を元にした譜面データの入力元
 */
public final class ByteArrayChartSource implements ChartSource {

	private final String location;
	private final byte[] data;

	public ByteArrayChartSource(String location, byte[] data) {
		this.location = location;
		this.data = Objects.requireNonNull(data, "data").clone();
	}

	@Override
	public String location() {
		return location;
	}

	@Override
	public InputStream openStream() {
		return new ByteArrayInputStream(data);
	}
}

