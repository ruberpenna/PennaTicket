-- 회원 테이블(V3)이 생기기 전, 임시 memberId로 만든 예매를 정리하고 FK를 건다.
-- (운영 DB는 미리 정리해서 대상이 없고, 주로 ticketing_test의 테스트 데이터가 해당된다)

-- 1. 회원이 없는 확정 예매의 좌석은 다시 예매 가능하게 되돌린다
update seat
   set status = 'AVAILABLE'
 where id in (select r.seat_id
                from reservation r
               where r.status = 'CONFIRMED'
                 and not exists (select 1 from member m where m.id = r.member_id));

-- 2. 회원이 없는 예매 삭제
delete from reservation r
 where not exists (select 1 from member m where m.id = r.member_id);

-- 3. 이제부터 없는 회원 id로는 예매가 저장되지 않는다
alter table reservation
    add constraint fk_reservation_member foreign key (member_id) references member (id);
