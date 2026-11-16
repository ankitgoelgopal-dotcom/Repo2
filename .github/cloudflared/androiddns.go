package main

// Android has no /etc/resolv.conf, so Go's resolver falls back to [::1]:53 and every lookup fails.
// This patch (applied to cloudflared by CI) makes it use the DNS servers handed over by the launcher
// in $MCP_DNS (comma separated), plus public fallbacks.

import (
	"context"
	"net"
	"os"
	"strings"
	"sync/atomic"
	"time"
)

var dnsCounter uint32

func init() {
	servers := []string{}
	for _, s := range strings.Split(os.Getenv("MCP_DNS"), ",") {
		s = strings.TrimSpace(s)
		if s != "" {
			servers = append(servers, net.JoinHostPort(s, "53"))
		}
	}
	servers = append(servers, "1.1.1.1:53", "8.8.8.8:53")
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			i := int(atomic.AddUint32(&dnsCounter, 1)) % len(servers)
			d := net.Dialer{Timeout: 5 * time.Second}
			return d.DialContext(ctx, network, servers[i])
		},
	}
}
