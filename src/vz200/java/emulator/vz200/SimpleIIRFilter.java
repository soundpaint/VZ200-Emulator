package emulator.vz200;

/**
 * Simple first-order IIR filter.
 */
public class SimpleIIRFilter
{
  private static final String MSG_ALPHA_OUT_OF_RANGE =
    "alpha too near to 0.0 for acceptable number of feed samples; " +
    "please choose a higher value for alpha";

  private final double alpha;
  private final double beta;
  private final long feedLength;
  private final double initialValue;
  private double value;

  /**
   * @param alpha The feedback of the filter is determined by beta :=
   * 1.0 - alpha.
   * @param resolution Targeted resolution, e.g. 32767 for integer
   * values (such as for a 16 bit audio input signal).  Needed to
   * estimate the maximum number of input samples ("feed length") to
   * be considered for reaching transient state.
   */
  public SimpleIIRFilter(final double alpha,
                         final long resolution,
                         final double initialValue)
  {
    if ((alpha < 0.0) || (alpha > 1.0)) {
      throw new IllegalArgumentException("alpha not in [0,1.0]");
    }
    this.alpha = alpha;
    beta = 1.0 - alpha;
    try {
      feedLength = determineFeedLength(beta, resolution);
    } catch (final RuntimeException e) {
      throw new IllegalArgumentException(MSG_ALPHA_OUT_OF_RANGE);
    }
    this.initialValue = initialValue;
    value = initialValue;
  }

  public double getAlpha()
  {
    return alpha;
  }

  public double getBeta()
  {
    return beta;
  }

  public long getFeedLength()
  {
    return feedLength;
  }

  public double getOutputValue()
  {
    return value;
  }

  public double addInputValue(final double value)
  {
    this.value = alpha * value + beta * this.value;
    return this.value;
  }

  public double resetToValue(final double value)
  {
    this.value = value;
    return value;
  }

  public double reset()
  {
    return resetToValue(initialValue);
  }

  private static long determineFeedLength(final double beta,
                                          final double resolution)
  {
    // minimum n such that beta^n < (1 / resolution)
    return (beta <= 0.0) ? 1 :
      (long)(Math.ceil(-Math.log(resolution) / Math.log(beta)) + 0.5);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
