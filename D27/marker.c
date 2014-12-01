#include <stdio.h>
int main () {
  int auth=0;  /* set by an authentication helper function, not shown here */
  char name[34];  /* student name */
  char mark[5];  /* student mark (% out of 100, up to 2 decimal digits) */
  char rubric[255];  /* rubric comment(s) (semicolon ";" separated) */

  puts("Name?");
  gets(name);
  puts("Mark?");
  gets(mark);
  puts("Rubric Comments?");
  gets(rubric);

  if (auth == 31337) {
    FILE *fp;
    fp = fopen("/courses/courses/cscd27f14/rosselet/asn/a3/marks/marks.html", "a");
    fprintf (fp, "<tr><td>%.40s</td><td>%s</td><td>%s</td></tr>\n", name, mark, rubric);
    close (fp);

    printf ("Successfully updated mark for student: %.40s (mark: %s) \n", name, mark);
  }

  return 0;
}
