// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

/**
 * Combines several Memory implementations to a unified memory
 * interface.  Hence, an implementor can implement RAM, ROM,
 * memory-mapped IO etc. as separate implementations of the Memory
 * interface and combine them as a chain of responsibility into a
 * single Memory interface implementation.
 */
public class ChainedMemory implements CPU.Memory {
  private CPU.Memory memory;
  private ChainedMemory next;

  public ChainedMemory() {}

  public void prependMemory(CPU.Memory memory) {
    if (memory == null)
      throw new NullPointerException("memory");
    if (this.memory != null) {
      ChainedMemory tail = new ChainedMemory();
      tail.memory = this.memory;
      tail.next = this.next;
      this.memory = memory;
      this.next = tail;
    } else {
      this.memory = memory;
    }
  }

  public void appendMemory(CPU.Memory memory) {
    if (memory == null)
      throw new NullPointerException("memory");
    if (this.memory != null) {
      if (next != null) {
	next.appendMemory(memory);
      } else {
	next = new ChainedMemory();
	next.memory = memory;
      }
    } else {
      this.memory = memory;
    }
  }

  public boolean isValidAddr(int address) {
    if (memory == null)
      throw new IllegalStateException("empty chained memory");
    return
      memory.isValidAddr(address) || next.isValidAddr(address);
  }

  public int readByte(int address) {
    if (memory == null)
      throw new IllegalStateException("empty chained memory");
    if (memory.isValidAddr(address))
      return memory.readByte(address);
    else if (next != null)
      return next.readByte(address);
    else
      return 0;
  }

  public int readShort(int address) {
    if (memory == null)
      throw new IllegalStateException("empty chained memory");
    if (memory.isValidAddr(address))
      return memory.readShort(address);
    else if (next != null)
      return next.readShort(address);
    else
      return 0;
  }

  public void writeByte(int address, int value) {
    if (memory == null)
      throw new IllegalStateException("empty chained memory");
    if (memory.isValidAddr(address))
      memory.writeByte(address, value);
    else if (next != null)
      next.writeByte(address, value);
  }

  public void writeShort(int address, int value) {
    if (memory == null)
      throw new IllegalStateException("empty chained memory");
    if (memory.isValidAddr(address))
      memory.writeShort(address, value);
    else if (next != null)
      next.writeShort(address, value);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
