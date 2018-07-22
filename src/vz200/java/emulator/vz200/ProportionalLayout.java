package emulator.vz200;

import java.awt.AWTError;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Toolkit;

/**
 * A layout manager that proportionally places all items on a panel,
 * given the preferred panel size and the proportional position for
 * each item.
 *
 * @author Jürgen Reuter
 */
class ProportionalLayout implements LayoutManager2 {
  private Container target;
  private float ratioXY;
  private float autoScale;
  private float autoOffsetX;
  private float autoOffsetY;
  private transient SizeRequirements[] xChildren;
  private transient SizeRequirements[] yChildren;
  private transient SizeRequirements xTotal;
  private transient SizeRequirements yTotal;

  private ProportionalLayout() {}

  public ProportionalLayout(Container target) {
    if (target == null)
      throw new NullPointerException("target");
    this.target = target;
  }

  private static class SizeRequirements {
    int minimum, preferred, maximum;
    float alignment;

    SizeRequirements() {
      minimum = 0;
      preferred = 0;
      maximum = 0;
      alignment = 0.5f;
    }

    SizeRequirements(int min, int pref, int max, float a) {
      minimum = min;
      preferred = pref;
      maximum = max;
      alignment = a > 1.0f ? 1.0f : a < 0.0f ? 0.0f : a;
    }

    static SizeRequirements getAlignedSizeRequirements(SizeRequirements[]
						       children) {
      SizeRequirements totalAscent = new SizeRequirements();
      SizeRequirements totalDescent = new SizeRequirements();
      for (int i = 0; i < children.length; i++) {
	SizeRequirements req = children[i];
	int ascent = (int)(req.alignment * req.minimum);
	int descent = req.minimum - ascent;
	totalAscent.minimum =
	  Math.max(ascent, totalAscent.minimum);
	totalDescent.minimum =
	  Math.max(descent, totalDescent.minimum);
	ascent = (int)(req.alignment * req.preferred);
	descent = req.preferred - ascent;
	totalAscent.preferred =
	  Math.max(ascent, totalAscent.preferred);
	totalDescent.preferred =
	  Math.max(descent, totalDescent.preferred);
	ascent = (int)(req.alignment * req.maximum);
	descent = req.maximum - ascent;
	totalAscent.maximum =
	  Math.max(ascent, totalAscent.maximum);
	totalDescent.maximum =
	  Math.max(descent, totalDescent.maximum);
      }
      int min = (int)Math.min((long)totalAscent.minimum +
			      (long)totalDescent.minimum,
			      Integer.MAX_VALUE);
      int pref = (int)Math.min((long)totalAscent.preferred +
			       (long)totalDescent.preferred,
			       Integer.MAX_VALUE);
      int max = (int)Math.min((long)totalAscent.maximum +
			      (long)totalDescent.maximum,
			      Integer.MAX_VALUE);
      float alignment = 0.0f;
      if (min > 0) {
	alignment = (float)totalAscent.minimum / min;
	alignment =
	  alignment > 1.0f ? 1.0f :
	  alignment < 0.0f ? 0.0f :
	  alignment;
      }
      return new SizeRequirements(min, pref, max, alignment);
    }

    static void calculateAlignedPositions(int allocated,
					  SizeRequirements total,
					  SizeRequirements[] children,
					  int[] offsets,
					  int[] spans) {
      float totalAlignment = 1.0f - total.alignment;
      int totalAscent = (int)(allocated * totalAlignment);
      int totalDescent = allocated - totalAscent;
      for (int i = 0; i < children.length; i++) {
	SizeRequirements req = children[i];
	float alignment = 1.0f - req.alignment;
	int maxAscent = (int)(req.maximum * alignment);
	int maxDescent = req.maximum - maxAscent;
	int ascent = Math.min(totalAscent, maxAscent);
	int descent = Math.min(totalDescent, maxDescent);
	offsets[i] = totalAscent - ascent;
	spans[i] = (int)Math.min((long)ascent + (long)descent,
				 Integer.MAX_VALUE);
      }
    }
  }

  public synchronized void invalidateLayout(Container target) {
    checkContainer(target);
    xChildren = null;
    yChildren = null;
    xTotal = null;
    yTotal = null;
  }

  /**
   * Not used by this class.
   */
  public void addLayoutComponent(String name, Component comp) {
  }

  /**
   * Not used by this class.
   */
  public void addLayoutComponent(Component comp, Object constraints) {
  }

  /**
   * Not used by this class.
   */
  public void removeLayoutComponent(Component comp) {
  }

  public Dimension preferredLayoutSize(Container parent) {
    Dimension size;
    synchronized(this) {
      checkContainer(target);
      checkRequests();
      size = new Dimension(xTotal.preferred, yTotal.preferred);
    }
    Insets insets = target.getInsets();
    size.width = (int)Math.min((long)size.width +
			       (long) insets.left +
			       (long) insets.right,
			       Integer.MAX_VALUE);
    size.height = (int)Math.min((long) size.height +
				(long) insets.top +
				(long) insets.bottom,
				Integer.MAX_VALUE);
    return size;
  }

  public Dimension minimumLayoutSize(Container parent) {
    Dimension size;
    synchronized(this) {
      checkContainer(target);
      checkRequests();
      size = new Dimension(xTotal.minimum, yTotal.minimum);
    }
    Insets insets = target.getInsets();
    size.width = (int)Math.min((long)size.width +
			       (long) insets.left +
			       (long) insets.right,
			       Integer.MAX_VALUE);
    size.height = (int)Math.min((long) size.height +
				(long) insets.top +
				(long) insets.bottom,
				Integer.MAX_VALUE);
    return size;
  }

  public Dimension maximumLayoutSize(Container target) {
    Dimension size;
    synchronized(this) {
      checkContainer(target);
      checkRequests();
      size = new Dimension(xTotal.maximum, yTotal.maximum);
    }
    Insets insets = target.getInsets();
    size.width = (int)Math.min((long)size.width +
			       (long) insets.left +
			       (long) insets.right,
			       Integer.MAX_VALUE);
    size.height = (int)Math.min((long) size.height +
				(long) insets.top +
				(long) insets.bottom,
				Integer.MAX_VALUE);
    return size;
  }

  public synchronized float getLayoutAlignmentX(Container target) {
    checkContainer(target);
    checkRequests();
    return xTotal.alignment;
  }

  public synchronized float getLayoutAlignmentY(Container target) {
    checkContainer(target);
    checkRequests();
    return yTotal.alignment;
  }

  private void checkContainer(Container target) {
    if (this.target != target) {
      throw new AWTError("ProportionalLayout can't be shared");
    }
  }

  private float getAlignmentX(Component target) {
    return autoOffsetX + autoScale * target.getAlignmentX();
  }

  private float getAlignmentY(Component target) {
    return autoOffsetY + autoScale * target.getAlignmentY();
  }

  public void layoutContainer(Container parent) {
    // FIXME: when the x size of target is smaller than preferred x
    // size, the y size currently screws up.
    checkContainer(target);
    Dimension targetSize = target.getSize();
    Insets in = target.getInsets();
    int nChildren = target.getComponentCount();
    checkRequests();
    float sizeX = targetSize.width - in.left - in.right;
    float sizeY = targetSize.height - in.top - in.bottom;
    float offsetX, offsetY;
    if (sizeY == 0.0f) {
      offsetY = 0.0f;
      offsetX = sizeX * getAlignmentX(target);
      sizeX = 0.0f;
    } else if (sizeX / sizeY >= ratioXY) {
      float newSizeX = sizeY * ratioXY;
      offsetX = getAlignmentX(target) * (sizeX - newSizeX);
      offsetY = 0.0f;
      sizeX = newSizeX;
    } else {
      float newSizeY = sizeX / ratioXY;
      offsetX = 0.0f;
      offsetY = getAlignmentY(target) * (sizeY - newSizeY);
      sizeY = newSizeY;
    }
    for (int i = 0; i < nChildren; i++) {
      Component child = target.getComponent(i);
      float childAlignX = getAlignmentX(child);
      float childAlignY = getAlignmentY(child);
      Dimension childPreferredSize = child.getPreferredSize();
      float childSpanX = childPreferredSize.width;
      float childSpanY = childPreferredSize.height;
      float childOffsetX = childAlignX * (sizeX - childSpanX);
      float childOffsetY = childAlignY * (sizeY - childSpanY);
      child.setBounds((int)(offsetX + childOffsetX),
		      (int)(offsetY + childOffsetY),
		      (int)childSpanX, (int)childSpanY);
    }
  }

  public void layoutContainer2(Container parent) {
    checkContainer(target);
    int nChildren = target.getComponentCount();
    int xOffsets[] = new int[nChildren];
    int xSpans[] = new int[nChildren];
    int yOffsets[] = new int[nChildren];
    int ySpans[] = new int[nChildren];
    Dimension alloc = target.getSize();
    Insets in = target.getInsets();
    alloc.width -= in.left + in.right;
    alloc.height -= in.top + in.bottom;
    /*
     * TODO: Should inspect value of target.getComponentOrientation().
     */
    synchronized(this) {
      checkRequests();
      SizeRequirements.
	calculateAlignedPositions(alloc.width, xTotal,
				  xChildren, xOffsets,
				  xSpans);
      SizeRequirements.
	calculateAlignedPositions(alloc.height, yTotal,
				  yChildren, yOffsets,
				  ySpans);
    }
    for (int i = 0; i < nChildren; i++) {
      Component c = target.getComponent (i);
      c.setBounds((int)Math.min((long) in.left +
				(long) xOffsets[i],
				Integer.MAX_VALUE),
		  (int) Math.min((long) in.top +
				 (long) yOffsets[i],
				 Integer.MAX_VALUE),
		   xSpans[i], ySpans[i]);
    }
  }

  private static float min(float a, float b) {
    return (a < b) ? a : b;
  }

  private static float max(float a, float b) {
    return (a > b) ? a : b;
  }

  private void checkRequests() {
    if (xTotal == null) {
      // determine x/y ratio
      int n = target.getComponentCount();
      float minX = 1.0f, maxX = 0.0f;
      float minY = 1.0f, maxY = 0.0f;
      for (int i = 0; i < n; i++) {
	Component c = target.getComponent(i);
	float alignX = c.getAlignmentX();
	minX = min(minX, alignX);
	maxX = max(maxX, alignX);
	float alignY = c.getAlignmentY();
	minY = min(minY, alignY);
	maxY = max(maxY, alignY);
      }
      float sizeX = max(0.0f, maxX - minX);
      float sizeY = max(0.0f, maxY - minY);
      ratioXY = (sizeY > 0.0f) ? sizeX / sizeY : 0.0f;
      if (ratioXY > 1.0f)
	autoScale = 1.0f / sizeX;
      else if (sizeY > 0.0f)
	autoScale = 1.0f / sizeY;
      else if (sizeX > 0.0f)
	autoScale = 1.0f / sizeX;
      else
	autoScale = 1.0f;
      autoOffsetX = - minX * autoScale;
      autoOffsetY = - minY * autoScale;

      /*
       * For the horizontal properties of component c be:
       *
       * c_0 left bound coordinate (in pixels), c_1 right bound
       * coordinate (in pixels), c_a proportional alignment in the
       * range 0.0 (left aligned) to 1.0 (right aligned), and c_t the
       * (preferred) size of c.
       *
       * d_0, d_1, d_a, d_t be accordingly defined for a component d.
       *
       * t be the (horizontal) preferred size to determine.
       *
       * Note that c_0 = c_a * t - c_a * c_t, and c_1 = c_0 + c_t (and
       * accordingly for component d).  If (c_a == d_a), then c and d
       * always collide, and we do not care about them, since we can
       * not do anything against the collsision.  Otherwise, we assume
       * that c_a < d_a (if not, we just flip c and d).  Then c does
       * not horizontally collide with da if and only if
       *
       * d0 > c1 <=>
       *
       * d_a * t - d_a * d_t > c_a * t - c_a * c_t + c_t <=>
       *
       * d_a * t - c_a * t > d_a * d_t - c_a * c_t + c_t <=>
       *
       * t > (d_a * d_t - c_a * c_t + c_t) / (d_a - c_a)
       *
       * According to our assumption c_a < d_a, we are sure not to
       * devide by zero.
       *
       * Since t has to be greater for all such components c and d
       * with c_a < d_a, we have to compute the maximum of the above
       * division for all such pairs (c, d).
       *
       * We currently implement here a simple algorithm with O(n^2)
       * time (with n = number of components) for considering all
       * pairs (c, d) with (c_a < d_a).
       *
       * Note that by prior sorting of the components by their
       * alignment value with an O(n * log n) algorithm, a simple
       * linear walk through the sorted list of components would
       * deliver adjacent pairs (which is sufficient for collision
       * detection) in effectively O(n) time.  Only if a component is
       * added, the list of components would have to be updated to be
       * again in sorted order.
       */
      float maxWidth = 0.0f, maxHeight = 0.0f;
      for (int i = 0; i < n; i++) {
	Component c = target.getComponent(i);
	Dimension cPref = c.getPreferredSize();
	maxWidth = Math.max(maxWidth, cPref.width);
	maxHeight = Math.max(maxHeight, cPref.height);
	float cAlignX = getAlignmentX(c);
	float cAlignY = getAlignmentY(c);
	for (int j = 0; j < n; j++) {
	  Component d = target.getComponent(i);
	  Dimension dPref = d.getPreferredSize();
	  float dAlignX = getAlignmentX(d);
	  float dAlignY = getAlignmentY(d);
	  if (cAlignX < dAlignX) {
	    float potMaxWidth =
	      (dAlignX * dPref.width -
	       (cAlignX * + 1.0f) * cPref.width) /
	      (dAlignX - cAlignX);
	    maxWidth =
	      Math.max(maxWidth, potMaxWidth);
	  }
	  if (cAlignY < dAlignY) {
	    float potMaxHeight =
	      (dAlignY * dPref.height -
	       (cAlignY * + 1.0f) * cPref.height) /
	      (dAlignY - cAlignY);
	    maxHeight =
	      Math.max(maxHeight, potMaxHeight);
	  }
	}
      }
      if (maxWidth < 0.0f)
	maxWidth = 0.0f;
      if (maxHeight < 0.0f)
	maxHeight = 0.0f;
      if (maxHeight * ratioXY < maxWidth)
	maxHeight = maxWidth / ratioXY;
      else
	maxWidth = maxHeight * ratioXY;
      Dimension screenSize =
	Toolkit.getDefaultToolkit().getScreenSize();
      if (maxWidth > screenSize.width) {
	float shrink = screenSize.width / maxWidth;
	maxWidth *= shrink;
	maxHeight *= shrink;
      }
      if (maxHeight > screenSize.height) {
	float shrink = screenSize.height / maxHeight;
	maxWidth *= shrink;
	maxHeight *= shrink;
      }
      xTotal = new SizeRequirements();
      yTotal = new SizeRequirements();
      xTotal.minimum = (int)maxWidth;
      xTotal.preferred = (int)maxWidth;
      xTotal.maximum = Integer.MAX_VALUE;
      xTotal.alignment = 0.5f;
      yTotal.minimum = (int)maxHeight;
      yTotal.preferred = (int)maxHeight;
      yTotal.maximum = Integer.MAX_VALUE;
      yTotal.alignment = 0.5f;
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 * End:
 */
