create table findme (
  sipaddress varchar(80) not null,
  backup_sipaddress varchar(80) not null,
  seq_no integer,
  primary key (sipaddress, seq_no)
);
COMMENT ON TABLE findme IS 'FMFM Subscribers';
