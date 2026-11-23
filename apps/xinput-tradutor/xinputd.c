/*
 * xinputd — tradutor de controle "modo Xbox" (XInput, USB classe ff/5d/01) para a multimídia do carro.
 *
 * O kernel da central (3.18, Jancar/MTK) não tem o driver xpad: receptores como o do 8BitDo
 * Ultimate 2C só falam XInput e ficam sem nada atendendo. Este programa roda como serviço root:
 * acha o receptor pelo /sys, lê os relatórios pelo usbfs e cria um controle virtual (uinput)
 * com botões e eixos padrão, que o Android, o Pegasus e o RetroArch entendem.
 * Um processo filho por receptor; reconecta sozinho quando o controle é tirado e posto de novo.
 */
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#include <linux/usbdevice_fs.h>
#include <signal.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>
#include <android/log.h>

#define TAG "xinputd"
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define MAXDEV 8
static char ativos[MAXDEV][64];
static pid_t pids[MAXDEV];

static int ler_arquivo(const char *dir, const char *nome, char *out, size_t n) {
    char p[512];
    snprintf(p, sizeof p, "%s/%s", dir, nome);
    FILE *f = fopen(p, "r");
    if (!f) return -1;
    if (!fgets(out, (int)n, f)) { fclose(f); return -1; }
    fclose(f);
    out[strcspn(out, "\r\n")] = 0;
    return 0;
}

static int hex(const char *dir, const char *nome) {
    char b[32];
    if (ler_arquivo(dir, nome, b, sizeof b)) return -1;
    return (int)strtol(b, NULL, 16);
}

/* Procura nos endpoints da interface o IN e o OUT de interrupção. */
static void endpoints(const char *ifdir, int *ein, int *eout) {
    *ein = *eout = -1;
    DIR *d = opendir(ifdir);
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d))) {
        if (strncmp(e->d_name, "ep_", 3)) continue;
        char p[512], tipo[32], dirc[16];
        snprintf(p, sizeof p, "%s/%s", ifdir, e->d_name);
        if (ler_arquivo(p, "type", tipo, sizeof tipo) || ler_arquivo(p, "direction", dirc, sizeof dirc)) continue;
        if (strcmp(tipo, "Interrupt")) continue;
        int ep = (int)strtol(e->d_name + 3, NULL, 16);
        if (!strcmp(dirc, "in") && *ein < 0) *ein = ep;
        if (!strcmp(dirc, "out") && *eout < 0) *eout = ep;
    }
    closedir(d);
}

static void abs_cfg(struct uinput_user_dev *u, int fd, int eixo, int min, int max, int fuzz, int flat) {
    ioctl(fd, UI_SET_ABSBIT, eixo);
    u->absmin[eixo] = min; u->absmax[eixo] = max; u->absfuzz[eixo] = fuzz; u->absflat[eixo] = flat;
}

static int criar_uinput(void) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { LOG("uinput: %s", strerror(errno)); return -1; }
    struct uinput_user_dev u;
    memset(&u, 0, sizeof u);
    snprintf(u.name, UINPUT_MAX_NAME_SIZE, "Controle Xbox (tradutor)");
    u.id.bustype = BUS_USB; u.id.vendor = 0x1209; u.id.product = 0x0360; u.id.version = 1;
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    ioctl(fd, UI_SET_EVBIT, EV_ABS);
    int botoes[] = {BTN_A, BTN_B, BTN_X, BTN_Y, BTN_TL, BTN_TR, BTN_TL2, BTN_TR2,
                    BTN_SELECT, BTN_START, BTN_MODE, BTN_THUMBL, BTN_THUMBR};
    for (unsigned i = 0; i < sizeof botoes / sizeof *botoes; i++) ioctl(fd, UI_SET_KEYBIT, botoes[i]);
    abs_cfg(&u, fd, ABS_X, -32768, 32767, 16, 128);
    abs_cfg(&u, fd, ABS_Y, -32768, 32767, 16, 128);
    abs_cfg(&u, fd, ABS_RX, -32768, 32767, 16, 128);
    abs_cfg(&u, fd, ABS_RY, -32768, 32767, 16, 128);
    abs_cfg(&u, fd, ABS_Z, 0, 255, 0, 0);
    abs_cfg(&u, fd, ABS_RZ, 0, 255, 0, 0);
    abs_cfg(&u, fd, ABS_HAT0X, -1, 1, 0, 0);
    abs_cfg(&u, fd, ABS_HAT0Y, -1, 1, 0, 0);
    if (write(fd, &u, sizeof u) != sizeof u || ioctl(fd, UI_DEV_CREATE) < 0) {
        LOG("uinput create: %s", strerror(errno));
        close(fd);
        return -1;
    }
    return fd;
}

static void emitir(int fd, int tipo, int cod, int val) {
    struct input_event ev;
    memset(&ev, 0, sizeof ev);
    ev.type = tipo; ev.code = cod; ev.value = val;
    if (write(fd, &ev, sizeof ev) < 0) { /* ignora */ }
}

static int s16(const unsigned char *b) { return (short)(b[0] | (b[1] << 8)); }

/* Atende um receptor até ele sumir. */
static void atender(const char *usbdev, int iface, int ein, int eout) {
    int fd = open(usbdev, O_RDWR);
    if (fd < 0) { LOG("abrir %s: %s", usbdev, strerror(errno)); return; }
    struct usbdevfs_ioctl dc = {iface, USBDEVFS_DISCONNECT, NULL};
    ioctl(fd, USBDEVFS_IOCTL, &dc); /* tira algum driver que tenha pego a interface */
    if (ioctl(fd, USBDEVFS_CLAIMINTERFACE, &iface) < 0) {
        LOG("claim %s: %s", usbdev, strerror(errno));
        close(fd);
        return;
    }
    if (eout >= 0) { /* acende o LED do jogador 1 (alguns receptores só mandam dados depois disso) */
        unsigned char led[3] = {0x01, 0x03, 0x02};
        struct usbdevfs_bulktransfer bt = {(unsigned)eout, sizeof led, 500, led};
        ioctl(fd, USBDEVFS_BULK, &bt);
    }
    int ui = criar_uinput();
    if (ui < 0) { close(fd); return; }
    LOG("controle ligado: %s (if %d, in 0x%02x)", usbdev, iface, ein);

    unsigned char b[64];
    for (;;) {
        struct usbdevfs_bulktransfer bt = {(unsigned)ein, sizeof b, 1000, b};
        int n = ioctl(fd, USBDEVFS_BULK, &bt);
        if (n < 0) {
            if (errno == ETIMEDOUT) continue;
            LOG("leitura terminou (%s)", strerror(errno));
            break;
        }
        if (n < 14 || b[0] != 0x00 || b[1] < 0x14) continue;
        int d = b[2], e = b[3];
        emitir(ui, EV_ABS, ABS_HAT0Y, (d & 0x01) ? -1 : (d & 0x02) ? 1 : 0);
        emitir(ui, EV_ABS, ABS_HAT0X, (d & 0x04) ? -1 : (d & 0x08) ? 1 : 0);
        emitir(ui, EV_KEY, BTN_START, !!(d & 0x10));
        emitir(ui, EV_KEY, BTN_SELECT, !!(d & 0x20));
        emitir(ui, EV_KEY, BTN_THUMBL, !!(d & 0x40));
        emitir(ui, EV_KEY, BTN_THUMBR, !!(d & 0x80));
        emitir(ui, EV_KEY, BTN_TL, !!(e & 0x01));
        emitir(ui, EV_KEY, BTN_TR, !!(e & 0x02));
        emitir(ui, EV_KEY, BTN_MODE, !!(e & 0x04));
        emitir(ui, EV_KEY, BTN_A, !!(e & 0x10));
        emitir(ui, EV_KEY, BTN_B, !!(e & 0x20));
        emitir(ui, EV_KEY, BTN_X, !!(e & 0x40));
        emitir(ui, EV_KEY, BTN_Y, !!(e & 0x80));
        emitir(ui, EV_ABS, ABS_Z, b[4]);
        emitir(ui, EV_ABS, ABS_RZ, b[5]);
        emitir(ui, EV_KEY, BTN_TL2, b[4] > 100);
        emitir(ui, EV_KEY, BTN_TR2, b[5] > 100);
        emitir(ui, EV_ABS, ABS_X, s16(b + 6));
        emitir(ui, EV_ABS, ABS_Y, -1 - s16(b + 8));   /* XInput: cima é positivo; Linux: cima é negativo */
        emitir(ui, EV_ABS, ABS_RX, s16(b + 10));
        emitir(ui, EV_ABS, ABS_RY, -1 - s16(b + 12));
        emitir(ui, EV_SYN, SYN_REPORT, 0);
    }
    ioctl(ui, UI_DEV_DESTROY);
    close(ui);
    ioctl(fd, USBDEVFS_RELEASEINTERFACE, &iface);
    close(fd);
}

static int ativo(const char *k) {
    for (int i = 0; i < MAXDEV; i++) if (pids[i] > 0 && !strcmp(ativos[i], k)) return 1;
    return 0;
}

static void procurar(void) {
    DIR *d = opendir("/sys/bus/usb/devices");
    if (!d) return;
    struct dirent *e;
    while ((e = readdir(d))) {
        if (!strchr(e->d_name, ':')) continue; /* só interfaces: "2-1:1.0" */
        char ifdir[512];
        snprintf(ifdir, sizeof ifdir, "/sys/bus/usb/devices/%s", e->d_name);
        if (hex(ifdir, "bInterfaceClass") != 0xff || hex(ifdir, "bInterfaceSubClass") != 0x5d
                || hex(ifdir, "bInterfaceProtocol") != 0x01) continue;
        if (ativo(e->d_name)) continue;
        char devdir[512], bus[16], num[16];
        snprintf(devdir, sizeof devdir, "/sys/bus/usb/devices/%.*s", (int)(strchr(e->d_name, ':') - e->d_name), e->d_name);
        if (ler_arquivo(devdir, "busnum", bus, sizeof bus) || ler_arquivo(devdir, "devnum", num, sizeof num)) continue;
        int iface = hex(ifdir, "bInterfaceNumber"), ein, eout;
        endpoints(ifdir, &ein, &eout);
        if (ein < 0) continue;
        char usbdev[64];
        snprintf(usbdev, sizeof usbdev, "/dev/bus/usb/%03d/%03d", atoi(bus), atoi(num));
        for (int i = 0; i < MAXDEV; i++) {
            if (pids[i] > 0) continue;
            pid_t p = fork();
            if (p == 0) { atender(usbdev, iface, ein, eout); _exit(0); }
            if (p > 0) { pids[i] = p; snprintf(ativos[i], sizeof ativos[i], "%s", e->d_name); }
            break;
        }
    }
    closedir(d);
}

int main(void) {
    signal(SIGPIPE, SIG_IGN);
    LOG("iniciado");
    for (;;) {
        int st;
        pid_t p;
        while ((p = waitpid(-1, &st, WNOHANG)) > 0)
            for (int i = 0; i < MAXDEV; i++) if (pids[i] == p) pids[i] = 0;
        procurar();
        sleep(2);
    }
}
