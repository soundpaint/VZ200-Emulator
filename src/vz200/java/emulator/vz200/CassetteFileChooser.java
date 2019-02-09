package emulator.vz200;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class CassetteFileChooser extends JFileChooser
{
  private static final long serialVersionUID = 35066383232434466L;
  private static final FileFilter rawFileFilter =
    new FileNameExtensionFilter("Raw Audio Files, 44.1kHz, 16 bit PCM mono (.raw)",
                                "raw");

  private CassetteFileChooser()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CassetteFileChooser(final String dialogTitle,
                             final String approveButtonText)
  {
    setDialogTitle(dialogTitle);
    setDialogType(CassetteFileChooser.CUSTOM_DIALOG);
    setApproveButtonText(approveButtonText);
    setMultiSelectionEnabled(false);
    addChoosableFileFilter(rawFileFilter);
    setFileFilter(rawFileFilter);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
