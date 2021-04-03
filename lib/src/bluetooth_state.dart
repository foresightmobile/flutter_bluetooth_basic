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
      break;
    case 2:
      return BluetoothConnectionState.connected;
      break;
    case 0:
      return BluetoothConnectionState.disconnected;
      break;
    case 11:
      return BluetoothConnectionState.stateTurnOn;
      break;
    case 13:
      return BluetoothConnectionState.stateTurnOff;
      break;
    default:
      return BluetoothConnectionState.unknown;
  }
}
