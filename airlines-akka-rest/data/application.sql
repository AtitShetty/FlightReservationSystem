PRAGMA foreign_keys=OFF;
BEGIN TRANSACTION;
CREATE TABLE AIRLINEINFO (ID INTEGER PRIMARY KEY AUTOINCREMENT, NAME TEXT NOT NULL, CODE TEXT NOT NULL, FLIGHT_NO TEXT NOT_NULL, CAPACITY INTEGER NOT NULL);
INSERT INTO AIRLINEINFO VALUES(1,'AMERICANAIRLINES','AA','AA001',3);
INSERT INTO AIRLINEINFO VALUES(2,'AMERICANAIRLINES','AA','AA002',1);
INSERT INTO AIRLINEINFO VALUES(3,'BRITISHAIRLINES','BA','BA001',1);
INSERT INTO AIRLINEINFO VALUES(4,'CHINAAIRLINES','CA','CA001',1);
INSERT INTO AIRLINEINFO VALUES(5,'CHINAAIRLINES','CA','CA002',1);
CREATE TABLE TRANSACTIONS (ID INTEGER PRIMARY KEY AUTOINCREMENT, TRANSACTION_ID INTEGER NOT NUll UNIQUE, FLIGHT_NO TEXT NOT_NULL, SEGMENT TEXT NOT NULL, FOREIGN KEY (FLIGHT_NO) REFERENCES AIRLINEINFO (FLIGHT_NO));
DELETE FROM sqlite_sequence;
INSERT INTO sqlite_sequence VALUES('AIRLINEINFO',5);
COMMIT;
	