import 'package:flutter/services.dart';

class Oxygen15BridgeChannel {
  static const MethodChannel _channel =
      MethodChannel('com.appsfolder.livebridge/o15_bridge');

  static Future<void> upsertBridgeItem({
    required int id,
    required String sourcePackage,
    required String title,
    required String body,
    int? progress,
    bool indeterminate = false,
    String? otpCode,
    String category = 'general',
  }) async {
    await _channel.invokeMethod<void>('upsertBridgeItem', {
      'id': id,
      'sourcePackage': sourcePackage,
      'title': title,
      'body': body,
      'progress': progress,
      'indeterminate': indeterminate,
      'otpCode': otpCode,
      'category': category,
    });
  }

  static Future<void> dismissBridgeItem(int id) async {
    await _channel.invokeMethod<void>('dismissBridgeItem', {'id': id});
  }
}
