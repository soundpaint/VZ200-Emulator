// TODO: move this class into new package 'emulator.monitor'.
package emulator.z80;

import java.io.IOException;

// TODO: Rename into "TerminalController".
public interface Terminal extends CPUControlAutomatonListener
{
  void print(final String text);
  void println();
  void println(final String text);
  void flush();
  void logDebug(final String message);
  void logInfo(final String message);
  void logWarn(final String message);
  void logError(final String message);
  String readLine() throws IOException;
  //boolean inputSeen() throws IOException;
  //void abortScript();
  void setScript(final String script);
  void setPrompt(final String prompt);
  String getPrompt();
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
