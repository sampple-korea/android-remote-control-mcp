// vkbd.c - virtual USB keyboard via /dev/uinput (raw syscalls, no libc deps beyond musl static)
// Usage: vkbd "text to type"   |   vkbd - < file   (text read from stdin; avoids shell quoting)
// Supported characters: printable ASCII (letters, digits, US-layout symbols), space and '\n' (Enter).
// Any other byte is skipped.
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/ioctl.h>
#include <time.h>

#define EV_SYN 0x00
#define EV_KEY 0x01
#define SYN_REPORT 0
#define UI_SET_EVBIT 0x40045564
#define UI_SET_KEYBIT 0x40045565
#define UI_DEV_CREATE 0x5501
#define UI_DEV_DESTROY 0x5502
#define BUS_USB 0x03

struct input_event {
    long tv_sec;
    long tv_usec;
    unsigned short type;
    unsigned short code;
    unsigned int value;
};

struct uinput_user_dev {
    char name[80];
    unsigned short bustype, vendor, product, version;
    unsigned int ff_effects_max;
    int absmax[64];
    int absmin[64];
    int absfuzz[64];
    int absflat[64];
};

// Linux keycodes
#define KEY_1 2
#define KEY_2 3
#define KEY_3 4
#define KEY_4 5
#define KEY_5 6
#define KEY_6 7
#define KEY_7 8
#define KEY_8 9
#define KEY_9 10
#define KEY_0 11
#define KEY_MINUS 12
#define KEY_EQUAL 13
#define KEY_Q 16
#define KEY_W 17
#define KEY_E 18
#define KEY_R 19
#define KEY_T 20
#define KEY_Y 21
#define KEY_U 22
#define KEY_I 23
#define KEY_O 24
#define KEY_P 25
#define KEY_LEFTBRACE 26
#define KEY_RIGHTBRACE 27
#define KEY_ENTER 28
#define KEY_A 30
#define KEY_S 31
#define KEY_D 32
#define KEY_F 33
#define KEY_G 34
#define KEY_H 35
#define KEY_J 36
#define KEY_K 37
#define KEY_L 38
#define KEY_SEMICOLON 39
#define KEY_APOSTROPHE 40
#define KEY_GRAVE 41
#define KEY_LEFTSHIFT 42
#define KEY_BACKSLASH 43
#define KEY_Z 44
#define KEY_X 45
#define KEY_C 46
#define KEY_V 47
#define KEY_B 48
#define KEY_N 49
#define KEY_M 50
#define KEY_COMMA 51
#define KEY_DOT 52
#define KEY_SLASH 53
#define KEY_SPACE 57

static int fd;

static void emit(unsigned short type, unsigned short code, unsigned int value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type = type;
    ev.code = code;
    ev.value = value;
    if (write(fd, &ev, sizeof(ev)) < 0) { /* ignore */ }
}

static void key(int code, int down) {
    emit(EV_KEY, code, down ? 1 : 0);
    emit(EV_SYN, SYN_REPORT, 0);
}

static void msleep(long ms) {
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000L;
    nanosleep(&ts, NULL);
}

// keycode for a lowercase letter, or -1
static int letter_key(char c) {
    static const int row1[] = {KEY_Q,KEY_W,KEY_E,KEY_R,KEY_T,KEY_Y,KEY_U,KEY_I,KEY_O,KEY_P};
    static const int row2[] = {KEY_A,KEY_S,KEY_D,KEY_F,KEY_G,KEY_H,KEY_J,KEY_K,KEY_L};
    static const int row3[] = {KEY_Z,KEY_X,KEY_C,KEY_V,KEY_B,KEY_N,KEY_M};
    const char *r1 = "qwertyuiop", *r2 = "asdfghjkl", *r3 = "zxcvbnm";
    const char *p;
    if ((p = strchr(r1, c))) return row1[p - r1];
    if ((p = strchr(r2, c))) return row2[p - r2];
    if ((p = strchr(r3, c))) return row3[p - r3];
    return -1;
}

// returns keycode for char, sets *shift
static int char_to_key(char c, int *shift) {
    *shift = 0;
    if (c >= 'a' && c <= 'z') return letter_key(c);
    if (c >= 'A' && c <= 'Z') {
        *shift = 1;
        return letter_key(c - 'A' + 'a');
    }
    if (c >= '1' && c <= '9') return KEY_1 + (c - '1');
    switch (c) {
        case '0': return KEY_0;
        case ' ': return KEY_SPACE;
        case '.': return KEY_DOT;
        case ',': return KEY_COMMA;
        case '-': return KEY_MINUS;
        case '=': return KEY_EQUAL;
        case ';': return KEY_SEMICOLON;
        case '\'': return KEY_APOSTROPHE;
        case '`': return KEY_GRAVE;
        case '[': return KEY_LEFTBRACE;
        case ']': return KEY_RIGHTBRACE;
        case '\\': return KEY_BACKSLASH;
        case '/': return KEY_SLASH;
        case '\n': return KEY_ENTER;
        // shifted symbols
        case '!': *shift = 1; return KEY_1;
        case '@': *shift = 1; return KEY_2;
        case '#': *shift = 1; return KEY_3;
        case '$': *shift = 1; return KEY_4;
        case '%': *shift = 1; return KEY_5;
        case '^': *shift = 1; return KEY_6;
        case '&': *shift = 1; return KEY_7;
        case '*': *shift = 1; return KEY_8;
        case '(': *shift = 1; return KEY_9;
        case ')': *shift = 1; return KEY_0;
        case '_': *shift = 1; return KEY_MINUS;
        case '+': *shift = 1; return KEY_EQUAL;
        case ':': *shift = 1; return KEY_SEMICOLON;
        case '"': *shift = 1; return KEY_APOSTROPHE;
        case '~': *shift = 1; return KEY_GRAVE;
        case '{': *shift = 1; return KEY_LEFTBRACE;
        case '}': *shift = 1; return KEY_RIGHTBRACE;
        case '|': *shift = 1; return KEY_BACKSLASH;
        case '<': *shift = 1; return KEY_COMMA;
        case '>': *shift = 1; return KEY_DOT;
        case '?': *shift = 1; return KEY_SLASH;
    }
    return -1;
}

static void type_char(char c) {
    int shift = 0;
    int code = char_to_key(c, &shift);
    if (code < 0) return;
    if (shift) { key(KEY_LEFTSHIFT, 1); msleep(15); }
    key(code, 1);
    msleep(25);
    key(code, 0);
    if (shift) { msleep(15); key(KEY_LEFTSHIFT, 0); }
    msleep(35);
}

#define MAX_STDIN_TEXT 8192

// Reads the whole of stdin (up to MAX_STDIN_TEXT bytes) into a NUL-terminated static buffer.
static const char *read_stdin_text(void) {
    static char buf[MAX_STDIN_TEXT + 1];
    size_t used = 0;
    while (used < MAX_STDIN_TEXT) {
        ssize_t n = read(STDIN_FILENO, buf + used, MAX_STDIN_TEXT - used);
        if (n < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (n == 0) break;
        used += (size_t)n;
    }
    buf[used] = '\0';
    return buf;
}

int main(int argc, char **argv) {
    // "vkbd <text>" types the argument; "vkbd -" (or a bare "vkbd") reads the text from stdin.
    const char *s;
    if (argc >= 2 && strcmp(argv[1], "-") != 0) {
        s = argv[1];
    } else {
        s = read_stdin_text();
    }
    fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open /dev/uinput"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    // register all keycodes we might send
    static const int allkeys[] = {
        KEY_1,KEY_2,KEY_3,KEY_4,KEY_5,KEY_6,KEY_7,KEY_8,KEY_9,KEY_0,
        KEY_MINUS,KEY_EQUAL,KEY_Q,KEY_W,KEY_E,KEY_R,KEY_T,KEY_Y,KEY_U,KEY_I,KEY_O,KEY_P,
        KEY_LEFTBRACE,KEY_RIGHTBRACE,KEY_ENTER,KEY_A,KEY_S,KEY_D,KEY_F,KEY_G,KEY_H,KEY_J,
        KEY_K,KEY_L,KEY_SEMICOLON,KEY_APOSTROPHE,KEY_GRAVE,KEY_LEFTSHIFT,KEY_BACKSLASH,
        KEY_Z,KEY_X,KEY_C,KEY_V,KEY_B,KEY_N,KEY_M,KEY_COMMA,KEY_DOT,KEY_SLASH,KEY_SPACE
    };
    for (unsigned i = 0; i < sizeof(allkeys)/sizeof(int); i++)
        ioctl(fd, UI_SET_KEYBIT, allkeys[i]);
    struct uinput_user_dev udev;
    memset(&udev, 0, sizeof(udev));
    strncpy(udev.name, "vkbd", sizeof(udev.name) - 1);
    udev.bustype = BUS_USB;
    udev.vendor = 0x1234;
    udev.product = 0x5678;
    udev.version = 1;
    if (write(fd, &udev, sizeof(udev)) < 0) { perror("write udev"); return 1; }
    if (ioctl(fd, UI_DEV_CREATE) < 0) { perror("UI_DEV_CREATE"); return 1; }
    msleep(1200); // let Android enumerate the keyboard
    for (const char *p = s; *p; p++) type_char(*p);
    msleep(300);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
