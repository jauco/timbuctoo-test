public class List {
  private ListNode head = null;

  public List() {
  }

  public List(ListNode head) {
    this.head = head;
  }

  public void reverse() {
    ListNode cur, next, prev, nnext;

    if (head == null) {
      return;
    }
    cur = head;
    next = head.next;
    prev = null;

    while (next != null) {
      nnext = next.next;
      cur.next = prev;
      prev = cur;
      cur = next;
      next = nnext;
    }
    if (prev != null) {
      cur.next = prev;
    }
    head = cur;
  }

  public void print() {
    ListNode cur = head;
    System.out.print("[");
    while (cur != null) {
      System.out.print(cur.getValue());
      System.out.print(",");
      cur = cur.next;
    }
    System.out.println("]");
  }
}
