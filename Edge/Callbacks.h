#ifndef CALLBACKS_H
#define CALLBACKS_H

#include <BLEServer.h>

// Deklarasi class callback untuk mode operasional normal
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer);
    void onDisconnect(BLEServer* pServer);
};

#endif
