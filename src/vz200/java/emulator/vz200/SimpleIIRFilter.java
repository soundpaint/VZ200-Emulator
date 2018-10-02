package emulator.vz200;

/**
 * Simple first-order IIR filter.
 */
public class SimpleIIRFilter {
  private double alpha;
  private double beta;
  private long significantSamples;
  private double value;

  /**
   * @param alpha The feedback of the filter is determined by beta :=
   * 1.0 - alpha.
   * @param resolution Targeted resolution, e.g. 32767 for integer
   * values (such as for a 16 bit audio input signal).  Needed to
   * estimate the maximum number of input samples ("significant
   * samples") to be considered for reaching transient state.
   */
  public SimpleIIRFilter(double alpha, long resolution, double initialValue) {
    if ((alpha < 0.0) || (alpha > 1.0)) {
      throw new IllegalArgumentException("alpha not in [0,1.0]");
    }
    this.alpha = alpha;
    beta = 1.0 - alpha;
    try {
      significantSamples = getSignificantSamples(alpha, resolution);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("alpha too near to 0.0 for acceptable number of significant samples; " +
                                         "please choose a higher value for alpha");
    }
    value = initialValue;
  }

  public double getAlpha() {
    return alpha;
  }

  public double getBeta() {
    return beta;
  }

  public long getSignificantSamples() {
    return significantSamples;
  }

  public double getOutputValue() {
    return value;
  }

  public double addInputValue(double inputValue) {
    value = alpha * inputValue + beta * value;
    return value;
  }

  private static long getSignificantSamples(double alpha, double resolution) {
    double beta = 1.0 - alpha; // IIR feedback
    // return minimum n such that beta^n < 1 / resolution
    return (long)(Math.ceil(-Math.log(resolution) / Math.log(beta)) + 0.5);
  }

}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
