// Copyright 2003-2005 Arthur van Hoff, Rick Blair
// Licensed under Apache License version 2.0
// Original license LGPL

package javax.jmdns.impl;

import java.io.IOException;
import java.net.DatagramPacket;

import javax.jmdns.impl.constants.DNSConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen for multicast packets.
 */
class SocketListener extends Thread {
    static Logger           logger = LoggerFactory.getLogger(SocketListener.class);

    /**
     *
     */
    private final JmDNSImpl _jmDNSImpl;

    /**
     * @param jmDNSImpl
     */
    SocketListener(JmDNSImpl jmDNSImpl) {
        super("SocketListener(" + (jmDNSImpl != null ? jmDNSImpl.getName() : "") + ")");
        this.setDaemon(true);
        this._jmDNSImpl = jmDNSImpl;
    }

    private void sleepThread() {
        if (_jmDNSImpl._threadSleepDurationMs > 0) {
            try {
                // sleep a small amount of time in case the network is overloaded with mdns packets (some devices do this),
                // in order to allow other threads to get some cpu time
                Thread.sleep(_jmDNSImpl._threadSleepDurationMs);
            } catch (InterruptedException e) {
                logger.warn(this.getName() + ".run() interrupted ", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void run() {
        try {
            byte buf[] = new byte[DNSConstants.MAX_MSG_ABSOLUTE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (!this._jmDNSImpl.isCanceling() && !this._jmDNSImpl.isCanceled()) {
                sleepThread();
                packet.setLength(buf.length);
                this._jmDNSImpl.getSocket().receive(packet);
                if (this._jmDNSImpl.isCanceling() || this._jmDNSImpl.isCanceled() || this._jmDNSImpl.isClosing() || this._jmDNSImpl.isClosed()) {
                    break;
                }
                try {
                    if (this._jmDNSImpl.getLocalHost().shouldIgnorePacket(packet)) {
                        continue;
                    }

                    DNSIncoming msg = new DNSIncoming(packet);
                    if (msg.isValidResponseCode()) {
                        if (logger.isTraceEnabled()) {
                            logger.trace("{}.run() JmDNS in:{}", this.getName(), msg.print(true));
                        }
                        if (msg.isQuery()) {
                            if (packet.getPort() != DNSConstants.MDNS_PORT) {
                                this._jmDNSImpl.handleQuery(msg, packet.getAddress(), packet.getPort());
                            }
                            this._jmDNSImpl.handleQuery(msg, this._jmDNSImpl.getGroup(), DNSConstants.MDNS_PORT);
                        } else {
                            this._jmDNSImpl.handleResponse(msg);
                        }
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("{}.run() JmDNS in message with error code: {}", this.getName(), msg.print(true));
                        }
                    }
                } catch (IOException e) {
                    logger.warn(this.getName() + ".run() exception ", e);
                }
            }
        } catch (IOException e) {
            if (!this._jmDNSImpl.isCanceling() && !this._jmDNSImpl.isCanceled() && !this._jmDNSImpl.isClosing() && !this._jmDNSImpl.isClosed()) {
                logger.warn(this.getName() + ".run() exception ", e);
                this._jmDNSImpl.recover();
            }
        }
        logger.trace("{}.run() exiting.", this.getName());
    }

    public JmDNSImpl getDns() {
        return _jmDNSImpl;
    }

}
