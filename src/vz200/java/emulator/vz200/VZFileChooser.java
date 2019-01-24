package emulator.vz200;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class VZFileChooser extends JFileChooser
{
  private static final long serialVersionUID = 2678663536481581874L;
  private static final FileFilter vzFileFilter =
    new FileNameExtensionFilter("VZ Files (.vz)", "vz");

  private VZFileChooser()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public VZFileChooser(final String dialogTitle, final String approveButtonText)
  {
    setDialogTitle(dialogTitle);
    setDialogType(VZFileChooser.CUSTOM_DIALOG);
    setApproveButtonText(approveButtonText);
    setMultiSelectionEnabled(false);
    addChoosableFileFilter(vzFileFilter);
    setFileFilter(vzFileFilter);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
