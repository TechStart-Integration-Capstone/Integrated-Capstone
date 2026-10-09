/// Pure Business Entity representing a User Profile
class UserEntity {
  final String username;
  final String email;
  final String status;

  const UserEntity({
    required this.username,
    required this.email,
    this.status = 'ACTIVE',
  });
}
