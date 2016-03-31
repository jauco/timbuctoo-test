public class Test {
  public static void main(String[] args) {
    test(new List());
    test(new List(new ListNode(1)));
    test(new List(new ListNode(1, new ListNode(2))));
    test(new List(new ListNode(1, new ListNode(2, new ListNode(3)))));
    test(new List(new ListNode(1, new ListNode(2, new ListNode(3, new ListNode(4))))));
  }

  public static void test(List l) {
    l.print();
    l.reverse();
    l.print();
  }
}
