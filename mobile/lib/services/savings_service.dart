import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'api_client.dart';

class SavingsOverview {
  final int customerId;
  final double personalTotal;
  final double groupTotal;
  final int totalGoalsCount;
  final int completedGoalsCount;
  final List<SavingsGoalItem> goals;

  SavingsOverview({
    required this.customerId,
    required this.personalTotal,
    required this.groupTotal,
    required this.totalGoalsCount,
    required this.completedGoalsCount,
    required this.goals,
  });

  factory SavingsOverview.fromJson(Map<String, dynamic> json) {
    final rawGoals = json['goals'] as List<dynamic>? ?? [];
    return SavingsOverview(
      customerId: (json['customerId'] as num?)?.toInt() ?? 0,
      personalTotal: (json['personalTotal'] as num?)?.toDouble() ?? 0.0,
      groupTotal: (json['groupTotal'] as num?)?.toDouble() ?? 0.0,
      totalGoalsCount: (json['count'] as num?)?.toInt() ?? rawGoals.length,
      completedGoalsCount: (json['completed'] as num?)?.toInt() ?? 0,
      goals: rawGoals.map((g) => SavingsGoalItem.fromJson(g as Map<String, dynamic>)).toList(),
    );
  }
}

class SavingsGoalItem {
  final String goalId;
  final String name;
  final String category;
  final double targetAmount;
  final double savedAmount;
  final String targetDate;
  final int accountId;
  final String? circleId;
  final int streak;
  final SavingsScheduleItem? schedule;

  SavingsGoalItem({
    required this.goalId,
    required this.name,
    required this.category,
    required this.targetAmount,
    required this.savedAmount,
    required this.targetDate,
    required this.accountId,
    this.circleId,
    this.streak = 0,
    this.schedule,
  });

  bool get isCompleted => savedAmount >= targetAmount && targetAmount > 0;
  double get progressRatio => targetAmount > 0 ? (savedAmount / targetAmount).clamp(0.0, 1.0) : 0.0;
  int get progressPercent => (progressRatio * 100).round();

  factory SavingsGoalItem.fromJson(Map<String, dynamic> json) {
    SavingsScheduleItem? sched;
    if (json['schedule'] != null) {
      if (json['schedule'] is List && (json['schedule'] as List).isNotEmpty) {
        sched = SavingsScheduleItem.fromJson((json['schedule'] as List).first as Map<String, dynamic>);
      } else if (json['schedule'] is Map<String, dynamic>) {
        sched = SavingsScheduleItem.fromJson(json['schedule'] as Map<String, dynamic>);
      }
    }

    return SavingsGoalItem(
      goalId: json['goal_id']?.toString() ?? json['goalId']?.toString() ?? '',
      name: json['name']?.toString() ?? 'Savings Goal',
      category: json['category']?.toString().toUpperCase() ?? 'OTHER',
      targetAmount: (json['target_amount'] as num?)?.toDouble() ?? (json['target'] as num?)?.toDouble() ?? 0.0,
      savedAmount: (json['savedAmount'] as num?)?.toDouble() ?? 0.0,
      targetDate: json['target_date']?.toString() ?? json['targetDate']?.toString() ?? '',
      accountId: (json['account_id'] as num?)?.toInt() ?? (json['accountId'] as num?)?.toInt() ?? 0,
      circleId: json['circle_id']?.toString() ?? json['circleId']?.toString(),
      streak: (json['streak'] as num?)?.toInt() ?? 0,
      schedule: sched,
    );
  }
}

class SavingsScheduleItem {
  final bool enabled;
  final double amount;
  final String frequency;
  final String nextDue;

  SavingsScheduleItem({
    required this.enabled,
    required this.amount,
    required this.frequency,
    required this.nextDue,
  });

  factory SavingsScheduleItem.fromJson(Map<String, dynamic> json) {
    return SavingsScheduleItem(
      enabled: json['enabled'] == true || json['enabled'] == 1,
      amount: (json['amount'] as num?)?.toDouble() ?? 0.0,
      frequency: json['frequency']?.toString().toUpperCase() ?? 'MONTHLY',
      nextDue: json['next_due']?.toString() ?? json['nextDue']?.toString() ?? '',
    );
  }
}

class PinkCircleItem {
  final String circleId;
  final String name;
  final double targetAmount;
  final String targetDate;
  final double savedAmount;
  final String membershipStatus;
  final int adminCustomerId;
  final List<PinkCircleMemberItem> members;

  PinkCircleItem({
    required this.circleId,
    required this.name,
    required this.targetAmount,
    required this.targetDate,
    required this.savedAmount,
    required this.membershipStatus,
    required this.adminCustomerId,
    required this.members,
  });

  bool get isInvited => membershipStatus == 'INVITED';
  bool get isActive => membershipStatus == 'ACTIVE';
  double get progressRatio => targetAmount > 0 ? (savedAmount / targetAmount).clamp(0.0, 1.0) : 0.0;
  int get progressPercent => (progressRatio * 100).round();

  factory PinkCircleItem.fromJson(Map<String, dynamic> json) {
    final rawMembers = json['members'] as List<dynamic>? ?? [];
    return PinkCircleItem(
      circleId: json['circle_id']?.toString() ?? json['circleId']?.toString() ?? '',
      name: json['name']?.toString() ?? 'PinkCircle',
      targetAmount: (json['target_amount'] as num?)?.toDouble() ?? 0.0,
      targetDate: json['target_date']?.toString() ?? '',
      savedAmount: (json['savedAmount'] as num?)?.toDouble() ?? 0.0,
      membershipStatus: json['membership_status']?.toString().toUpperCase() ?? 'ACTIVE',
      adminCustomerId: (json['admin_customer_id'] as num?)?.toInt() ?? 0,
      members: rawMembers.map((m) => PinkCircleMemberItem.fromJson(m as Map<String, dynamic>)).toList(),
    );
  }
}

class PinkCircleMemberItem {
  final int customerId;
  final String firstName;
  final String lastName;
  final String status;
  final double targetAmount;
  final double? savedAmount;
  final String? goalId;
  final double? proposedTarget;
  final bool shareProgress;

  PinkCircleMemberItem({
    required this.customerId,
    required this.firstName,
    required this.lastName,
    required this.status,
    required this.targetAmount,
    this.savedAmount,
    this.goalId,
    this.proposedTarget,
    this.shareProgress = false,
  });

  String get fullName => '$firstName $lastName'.trim();
  bool get isYou => goalId != null && goalId!.isNotEmpty;

  factory PinkCircleMemberItem.fromJson(Map<String, dynamic> json) {
    return PinkCircleMemberItem(
      customerId: (json['customer_id'] as num?)?.toInt() ?? (json['customerId'] as num?)?.toInt() ?? 0,
      firstName: json['first_name']?.toString() ?? json['firstName']?.toString() ?? '',
      lastName: json['last_name']?.toString() ?? json['lastName']?.toString() ?? '',
      status: json['status']?.toString().toUpperCase() ?? 'ACTIVE',
      targetAmount: (json['target_amount'] as num?)?.toDouble() ?? 0.0,
      savedAmount: json['savedAmount'] != null ? (json['savedAmount'] as num).toDouble() : null,
      goalId: json['goal_id']?.toString() ?? json['goalId']?.toString(),
      proposedTarget: json['proposed_target'] != null ? (json['proposed_target'] as num).toDouble() : null,
      shareProgress: json['share_progress'] == true || json['share_progress'] == 1,
    );
  }
}

class SavingsFundingDetails {
  final int accountId;
  final double accountBalance;
  final double reservedSavings;
  final double otherHolds;
  final double availableBalance;
  final double goalSavedAmount;
  final double remainingTarget;
  final double maximumContribution;

  SavingsFundingDetails({
    required this.accountId,
    required this.accountBalance,
    required this.reservedSavings,
    required this.otherHolds,
    required this.availableBalance,
    required this.goalSavedAmount,
    required this.remainingTarget,
    required this.maximumContribution,
  });

  factory SavingsFundingDetails.fromJson(Map<String, dynamic> json) {
    return SavingsFundingDetails(
      accountId: (json['accountId'] as num?)?.toInt() ?? 0,
      accountBalance: (json['accountBalance'] as num?)?.toDouble() ?? 0.0,
      reservedSavings: (json['reservedSavings'] as num?)?.toDouble() ?? 0.0,
      otherHolds: (json['otherHolds'] as num?)?.toDouble() ?? 0.0,
      availableBalance: (json['availableBalance'] as num?)?.toDouble() ?? 0.0,
      goalSavedAmount: (json['goalSavedAmount'] as num?)?.toDouble() ?? 0.0,
      remainingTarget: (json['remainingTarget'] as num?)?.toDouble() ?? 0.0,
      maximumContribution: (json['maximumContribution'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

class SavingsActivityItem {
  final String operationId;
  final int customerId;
  final int accountId;
  final String status;
  final String type; // 'ALLOCATE' or 'RELEASE'
  final double amount;
  final String goalId;
  final DateTime createdAt;

  SavingsActivityItem({
    required this.operationId,
    required this.customerId,
    required this.accountId,
    required this.status,
    required this.type,
    required this.amount,
    required this.goalId,
    required this.createdAt,
  });

  bool get isAllocate => type == 'ALLOCATE';
  bool get isRelease => type == 'RELEASE';

  static List<SavingsActivityItem> fromListJson(Map<String, dynamic> json) {
    final opId = json['operation_id']?.toString() ?? json['operationId']?.toString() ?? '';
    final custId = (json['customer_id'] as num?)?.toInt() ?? (json['customerId'] as num?)?.toInt() ?? 0;
    final acctId = (json['account_id'] as num?)?.toInt() ?? (json['accountId'] as num?)?.toInt() ?? 0;
    final st = json['status']?.toString().toUpperCase() ?? 'CONFIRMED';
    final rawDate = json['created_at']?.toString() ?? '';
    DateTime dt;
    try {
      final normalized = (rawDate.endsWith('Z') || rawDate.contains('+')) ? rawDate : '${rawDate}Z';
      dt = DateTime.parse(normalized).toLocal();
    } catch (_) {
      dt = DateTime.now();
    }

    String reqType = 'ALLOCATE';
    final linesList = <({String goalId, double amount})>[];

    if (json['request_json'] != null) {
      try {
        final req = jsonDecode(json['request_json'].toString());
        final cmd = req['command'] as Map<String, dynamic>?;
        if (cmd != null) {
          reqType = cmd['type']?.toString().toUpperCase() ?? 'ALLOCATE';
          final lines = cmd['lines'] as List<dynamic>? ?? [];
          for (final l in lines) {
            if (l is Map) {
              final gid = l['goalId']?.toString() ?? '';
              final amt = (l['amount'] as num?)?.toDouble() ?? 0.0;
              linesList.add((goalId: gid, amount: amt));
            }
          }
        }
      } catch (_) {}
    }

    if (linesList.isEmpty) {
      return [
        SavingsActivityItem(
          operationId: opId,
          customerId: custId,
          accountId: acctId,
          status: st,
          type: reqType,
          amount: 0.0,
          goalId: '',
          createdAt: dt,
        )
      ];
    }

    return linesList
        .map((l) => SavingsActivityItem(
              operationId: opId,
              customerId: custId,
              accountId: acctId,
              status: st,
              type: reqType,
              amount: l.amount,
              goalId: l.goalId,
              createdAt: dt,
            ))
        .toList();
  }
}

class SavingsService {
  static final ApiClient _api = ApiClient();
  static const String _base = '/accounts/savings';

  /// Fetches savings activity history: GET /api/v1/accounts/savings/activity
  static Future<List<SavingsActivityItem>> fetchActivity({String? goalId}) async {
    try {
      final res = await _api.get('$_base/activity');
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        if (data is List) {
          final items = <SavingsActivityItem>[];
          for (final raw in data) {
            if (raw is Map<String, dynamic>) {
              try {
                final list = SavingsActivityItem.fromListJson(raw);
                for (final item in list) {
                  if (goalId == null || item.goalId == goalId) {
                    items.add(item);
                  }
                }
              } catch (e) {
                debugPrint('[SavingsService] parse activity item error: $e');
              }
            }
          }
          return items;
        }
      }
      return [];
    } catch (e) {
      debugPrint('[SavingsService] fetchActivity error: $e');
      return [];
    }
  }

  /// Fetches personal savings overview and goals: GET /api/v1/accounts/savings
  static Future<SavingsOverview?> fetchOverview() async {
    try {
      final res = await _api.get(_base);
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        return SavingsOverview.fromJson(data);
      }
      debugPrint('[SavingsService] fetchOverview returned status ${res.statusCode}');
      return null;
    } catch (e) {
      debugPrint('[SavingsService] fetchOverview error: $e');
      return null;
    }
  }

  /// Fetches all active & invited PinkCircles: GET /api/v1/accounts/savings/circles
  static Future<List<PinkCircleItem>> fetchCircles() async {
    try {
      final res = await _api.get('$_base/circles');
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        if (data is List) {
          return data.map((c) => PinkCircleItem.fromJson(c as Map<String, dynamic>)).toList();
        }
      }
      return [];
    } catch (e) {
      debugPrint('[SavingsService] fetchCircles error: $e');
      return [];
    }
  }

  /// Checks live available funding snapshot before contributing: GET /api/v1/accounts/savings/goals/{id}/funding
  static Future<SavingsFundingDetails?> checkFunding(String goalId) async {
    try {
      final res = await _api.get('$_base/goals/$goalId/funding');
      if (res.statusCode == 200) {
        return SavingsFundingDetails.fromJson(jsonDecode(res.body));
      }
      return null;
    } catch (e) {
      debugPrint('[SavingsService] checkFunding error: $e');
      return null;
    }
  }

  /// Allocates (adds) or Releases money from a savings goal: POST /api/v1/accounts/savings/operations
  static Future<({bool success, String message})> executeOperation({
    required int accountId,
    required String goalId,
    required double amount,
    required String type, // 'ALLOCATE' or 'RELEASE'
  }) async {
    try {
      final key = ApiClient.generateIdempotencyKey();
      final body = {
        'accountId': accountId,
        'type': type,
        'lines': [
          {'goalId': goalId, 'amount': amount}
        ],
      };
      final res = await _api.post(
        '$_base/operations',
        body: body,
        idempotencyKey: key,
      );

      if (res.statusCode == 200 || res.statusCode == 202) {
        return (
          success: true,
          message: type == 'ALLOCATE'
              ? 'Successfully set aside ₱${amount.toStringAsFixed(2)} for your goal.'
              : 'Successfully released ₱${amount.toStringAsFixed(2)} back to your available balance.'
        );
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Savings operation failed (HTTP ${res.statusCode}).');
    } catch (e) {
      return (success: false, message: 'Unable to connect to savings service. Please try again.');
    }
  }

  /// Creates a personal savings goal: POST /api/v1/accounts/savings/goals
  static Future<({bool success, String message, String? goalId})> createGoal({
    required int accountId,
    required String name,
    required String category,
    required double target,
    required String targetDate,
    double initialAmount = 0.0,
  }) async {
    try {
      final body = {
        'accountId': accountId,
        'name': name.trim(),
        'category': category.toUpperCase(),
        'target': target,
        'targetDate': targetDate,
      };
      final res = await _api.post('$_base/goals', body: body);
      if (res.statusCode == 200 || res.statusCode == 201) {
        final data = jsonDecode(res.body);
        final gid = data['goalId']?.toString();
        if (initialAmount > 0 && gid != null) {
          await executeOperation(
            accountId: accountId,
            goalId: gid,
            amount: initialAmount,
            type: 'ALLOCATE',
          );
        }
        return (success: true, message: 'Goal created successfully!', goalId: gid);
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Failed to create goal.', goalId: null);
    } catch (e) {
      return (success: false, message: 'Unable to connect. Please check your network.', goalId: null);
    }
  }

  /// Creates a PinkCircle group savings: POST /api/v1/accounts/savings/circles
  static Future<({bool success, String message})> createCircle({
    required int accountId,
    required String name,
    required double target,
    required String targetDate,
    required double myTarget,
    bool shareProgress = true,
  }) async {
    try {
      final body = {
        'goal': {
          'accountId': accountId,
          'name': name.trim(),
          'category': 'OTHER',
          'target': target,
          'targetDate': targetDate,
        },
        'myTarget': myTarget,
        'shareProgress': shareProgress,
      };
      final res = await _api.post('$_base/circles', body: body);
      if (res.statusCode == 200 || res.statusCode == 201) {
        return (success: true, message: 'PinkCircle "$name" created successfully!');
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Failed to create PinkCircle.');
    } catch (e) {
      return (success: false, message: 'Unable to connect. Please check your network.');
    }
  }

  /// Invites a PayPink user to an active PinkCircle: POST /api/v1/accounts/savings/circles/{id}/invitations
  static Future<({bool success, String message})> inviteMember({
    required String circleId,
    required String username,
    required double target,
  }) async {
    try {
      final body = {
        'username': username.trim(),
        'target': target,
      };
      final res = await _api.post('$_base/circles/$circleId/invitations', body: body);
      if (res.statusCode == 200 || res.statusCode == 201 || res.statusCode == 204) {
        return (success: true, message: 'Invitation sent to $username!');
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Failed to invite user.');
    } catch (e) {
      return (success: false, message: 'Unable to connect. Please try again.');
    }
  }

  /// Accepts an invitation to join a PinkCircle: POST /api/v1/accounts/savings/circles/{id}/accept
  static Future<({bool success, String message})> acceptInvitation({
    required String circleId,
    required int accountId,
    bool shareProgress = true,
  }) async {
    try {
      final body = {
        'accountId': accountId,
        'shareProgress': shareProgress,
      };
      final res = await _api.post('$_base/circles/$circleId/accept', body: body);
      if (res.statusCode == 200 || res.statusCode == 201) {
        return (success: true, message: 'Joined PinkCircle successfully!');
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Failed to accept invitation.');
    } catch (e) {
      return (success: false, message: 'Unable to connect. Please try again.');
    }
  }

  /// Updates progress sharing privacy: PUT /api/v1/accounts/savings/circles/{id}/visibility
  static Future<bool> updateVisibility({
    required String circleId,
    required bool shareProgress,
  }) async {
    try {
      final res = await _api.put(
        '$_base/circles/$circleId/visibility',
        body: {'shareProgress': shareProgress},
      );
      return res.statusCode == 200 || res.statusCode == 204;
    } catch (_) {
      return false;
    }
  }

  /// Sets or updates automatic contribution schedule: PUT /api/v1/accounts/savings/goals/{id}/schedule
  static Future<({bool success, String message})> updateSchedule({
    required String goalId,
    required double amount,
    required String frequency,
    required String nextDue,
    required bool enabled,
  }) async {
    try {
      final body = {
        'amount': amount,
        'frequency': frequency.toUpperCase(),
        'nextDue': nextDue,
        'enabled': enabled,
      };
      final res = await _api.put('$_base/goals/$goalId/schedule', body: body);
      if (res.statusCode == 200 || res.statusCode == 204) {
        return (success: true, message: 'Savings plan updated!');
      }
      final err = ApiClient.extractErrorMessage(res.statusCode, res.body);
      return (success: false, message: err.isNotEmpty ? err : 'Failed to update schedule.');
    } catch (e) {
      return (success: false, message: 'Unable to connect. Please try again.');
    }
  }
}
