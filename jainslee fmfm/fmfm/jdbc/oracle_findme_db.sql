create table findme (
  sipaddress varchar(80) not null,
  backup_sipaddress varchar(80) not null,
  seq_no number(5)
);
CREATE UNIQUE INDEX PK_findme ON findme (sipaddress, seq_no);
COMMENT ON TABLE findme IS 'FMFM Subscribers';
