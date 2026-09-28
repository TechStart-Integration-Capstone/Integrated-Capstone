UPDATE CUSTOMER SET password_hash = '$2b$10$nQqX3kBciCLYCyofDR3b6Oy4X/.99Bap8d49IpzN3D8MCj20TuJOO' WHERE username IN ('lviernes','arosales','glim','jdelacruz');
COMMIT;
SELECT username, LENGTH(password_hash) hash_len FROM customer;
EXIT;
