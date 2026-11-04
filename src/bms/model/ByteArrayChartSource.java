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
	private final long lastModifiedTimeMillis;

	public ByteArrayChartSource(String location, byte[] data) {
		this(location, data, -1L);
	}

	public ByteArrayChartSource(String location, byte[] data, long lastModifiedTimeMillis) {
		this.location = location;
		this.data = Objects.requireNonNull(data, "data").clone();
		this.lastModifiedTimeMillis = lastModifiedTimeMillis;
	}

	@Override
	public long lastModifiedTimeMillis() {
		return lastModifiedTimeMillis;
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

