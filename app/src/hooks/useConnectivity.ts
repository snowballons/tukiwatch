import NetInfo from '@react-native-community/netinfo';
import { useEffect, useState } from 'react';

export interface ConnectivityState {
  /** OS-level link status. Null while NetInfo is still resolving. */
  isConnected: boolean | null;
  /** Whether the internet is actually reachable. Null while resolving. */
  isInternetReachable: boolean | null;
  /** True only when the OS explicitly reports no connection/reachability. */
  isOffline: boolean;
}

const INITIAL: ConnectivityState = {
  isConnected: null,
  isInternetReachable: null,
  isOffline: false,
};

function toState(
  isConnected: boolean | null,
  isInternetReachable: boolean | null
): ConnectivityState {
  return {
    isConnected,
    isInternetReachable,
    isOffline: isConnected === false || isInternetReachable === false,
  };
}

/** Live OS connectivity. Shared by every screen for banners/empty states. */
export function useConnectivity(): ConnectivityState {
  const [state, setState] = useState<ConnectivityState>(INITIAL);

  useEffect(() => {
    const subscription = NetInfo.addEventListener((netState) => {
      setState(toState(netState.isConnected, netState.isInternetReachable));
    });
    NetInfo.fetch()
      .then((netState) => {
        setState(toState(netState.isConnected, netState.isInternetReachable));
      })
      .catch(() => {
        // Keep initial state; screens fall back to request-level errors.
      });
    return () => subscription();
  }, []);

  return state;
}
