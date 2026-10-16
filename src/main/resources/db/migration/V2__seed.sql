-- 로컬에서 바로 API를 호출해 볼 수 있는 샘플 데이터
insert into performance (title, venue, start_at, created_at)
values ('레미제라블', '블루스퀘어', now() + interval '30 days', now());

-- A구역 VIP 10석, B구역 R석 20석
insert into seat (performance_id, section, seat_number, grade, price, status)
select 1, 'A', n::text, 'VIP', 170000, 'AVAILABLE' from generate_series(1, 10) as n;

insert into seat (performance_id, section, seat_number, grade, price, status)
select 1, 'B', n::text, 'R', 140000, 'AVAILABLE' from generate_series(1, 20) as n;
