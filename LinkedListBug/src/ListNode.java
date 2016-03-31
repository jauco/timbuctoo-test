public class ListNode {
  private int value;
  protected ListNode next;

  public ListNode(int value) {
    this.value = value;
  }

  public ListNode(int value, ListNode next) {
    this.value = value;
    this.next = next;
  }

  public int getValue() {
    return value;
  }

  public String toString() {
    return this.value + "";
  }

}