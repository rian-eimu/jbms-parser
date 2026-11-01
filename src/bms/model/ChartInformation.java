package bms.model;

import java.nio.file.Path;

public class ChartInformation {

	public final Path path;

	public final ChartSource source;
	
	public final int lntype;
	
	public final int[] selectedRandoms;
	
	public ChartInformation(Path path, int lntype, int[] selectedRandoms) {
		this(path, path != null ? new PathChartSource(path) : null, lntype, selectedRandoms);
	}

	public ChartInformation(ChartSource source, int lntype, int[] selectedRandoms) {
		this(source instanceof PathChartSource ? ((PathChartSource) source).path() : null, source, lntype, selectedRandoms);
	}

	private ChartInformation(Path path, ChartSource source, int lntype, int[] selectedRandoms) {
		this.path = path;
		this.source = source;
		this.lntype = lntype;
		this.selectedRandoms = selectedRandoms;
	}

}
