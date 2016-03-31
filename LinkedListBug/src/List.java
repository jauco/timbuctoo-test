public class List {
  private ListNode head = null;

  public List() {
  }

  public List(ListNode head) {
    this.head = head;
  }

  public void reverse() {
    ListNode ln1, ln2, ln3, ln4;

    if (head == null) {
      return;
    }
    ln1 = head;
    ln2 = head.next;
    ln3 = null;

    while (ln2 != null) {
      ln4 = ln2.next;
      ln1.next = ln3;
      ln3 = ln1;
      ln1 = ln2;
      ln2 = ln4;
    }
    head = ln1;
  }
}
