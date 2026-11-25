#include <stdio.h>
#include <stdlib.h>
#include <sys/utsname.h>
#include <unistd.h>

int main(void) {
    struct utsname system_info;
    if (uname(&system_info) != 0) {
        perror("uname");
        return 21;
    }

    printf("STORM_BIONIC_PROBE=PASS\n");
    printf("machine=%s\n", system_info.machine);
    printf("sysname=%s\n", system_info.sysname);
    printf("pid=%ld\n", (long)getpid());
    fflush(stdout);

    /* Reserved success code used by the Android-side runtime supervisor. */
    return 37;
}
