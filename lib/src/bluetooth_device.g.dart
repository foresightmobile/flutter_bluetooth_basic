// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'bluetooth_device.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

BluetoothDevice _$BluetoothDeviceFromJson(Map<String, dynamic> json) {
  return BluetoothDevice(
    name: json['name'] as String,
    address: json['address'] as String,
    type: json['type'] as int?,
    connected: json['connected'] as bool?,
  );
}

Map<String, dynamic> _$BluetoothDeviceToJson(BluetoothDevice instance) {
  final val = <String, dynamic>{
    'name': instance.name,
    'address': instance.address,
  };

  void writeNotNull(String key, dynamic value) {
    if (value != null) {
      val[key] = value;
    }
  }

  writeNotNull('type', instance.type);
  writeNotNull('connected', instance.connected);
  return val;
}
