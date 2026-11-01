package bms.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * ファイルシステム上のPathを元にした譜面データの入力元
 */
public final class PathChartSource implements ChartSource {

	private final Path path;

	public PathChartSource(Path path) {
		this.path = Objects.requireNonNull(path, "path");
	}

	public Path path() {
		return path;
	}

	@Override
	public String location() {
		return path.toString();
	}

	@Override
	public InputStream openStream() throws IOException {
		return Files.newInputStream(path);
	}
}

