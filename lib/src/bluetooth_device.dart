import 'package:json_annotation/json_annotation.dart';

part 'bluetooth_device.g.dart';

@JsonSerializable(includeIfNull: false)
class BluetoothDevice {
  BluetoothDevice(
      {required this.name, required this.address, this.type, this.connected});

  final String name;
  final String address;
  int? type = 0;
  bool? connected = false;

  factory BluetoothDevice.fromJson(Map<String, dynamic> json) =>
      _$BluetoothDeviceFromJson(json);
  Map<String, dynamic> toJson() => _$BluetoothDeviceToJson(this);
}
