enum BluetoothConnectionState {
  connected,
  disconnected,
  stateOn,
  stateOff,
  stateTurnOn,
  stateTurnOff,
  unknown,
}

BluetoothConnectionState bluetoothConnectionStateFromInt(v) {
  if (v is! int) return BluetoothConnectionState.unknown;

  switch (v) {
    case 10:
      return BluetoothConnectionState.stateOff;
    case 2:
      return BluetoothConnectionState.connected;
    case 0:
      return BluetoothConnectionState.disconnected;
    case 11:
      return BluetoothConnectionState.stateTurnOn;
    case 13:
      return BluetoothConnectionState.stateTurnOff;
    default:
      return BluetoothConnectionState.unknown;
  }
}
