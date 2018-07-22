// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

public class Util
{
  private final static char[] HEX_CHARS = {
    '0', '1', '2', '3', '4', '5', '6', '7',
    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  public static String hexByteStr(int b) {
    return "" + (HEX_CHARS[(b >>> 4) & 0xf]) + HEX_CHARS[b & 0xf];
  }

  public static String hexShortStr(int s) {
    return hexByteStr((s >>> 8)) + hexByteStr((s & 0xff));
  }

  public static String hexIntStr(int i) {
    return hexShortStr((i >>> 16)) + hexShortStr((i & 0xffff));
  }

  public static int hexValue(char literal) {
    if (('0' <= literal) && (literal <= '9'))
      return (byte)literal - (byte)'0';
    else if (('A' <= literal) && (literal <= 'F'))
      return (byte)literal - (byte)'A' + 10;
    else if (('a' <= literal) && (literal <= 'f'))
      return (byte)literal - (byte)'a' + 10;
    else
      return -1;
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
