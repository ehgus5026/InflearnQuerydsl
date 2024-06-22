package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import java.util.List;

import static com.querydsl.jpa.JPAExpressions.*;
import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;

    JPAQueryFactory queryFactory; // 여러 멀티 쓰레드에서 접근해도 동시성 문제를 해결해주게끔 설계 되어 있음.

    @BeforeEach
    public void before() {
        queryFactory = new JPAQueryFactory(em);
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);

        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
    }

    @Test
    public void startJPQL() {
        // member1을 찾아라.
        Member findByJPQL = em.createQuery("select m from Member m where m.username = :username", Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findByJPQL.getUsername()).isEqualTo("member1");
    }

    @Test
    public void startQuerydsl() {
//        QMember m = new QMember("m"); // "m"은 식별자 (같은 테이블을 조인해야 하는 경우가 아니면 기본 인스턴스를 사용)
//        QMember m = QMember.member;

        Member findMember = queryFactory
                .select(member) // static import (권장)
                .from(member)
                .where(member.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1")
                        .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
        assertThat(findMember.getAge()).isEqualTo(10);
    }

    @Test
    public void searchAndParam() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"), // where문에서 and는 ,(쉼표)로 사용 가능.
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
        assertThat(findMember.getAge()).isEqualTo(10);
    }

    @Test
    public void resultFetch() {
        // List
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        // 단건
        // 결과가 없으면 : `null`
        //결과가 둘 이상이면 : `com.querydsl.core.NonUniqueResultException
//        Member fetchOne = queryFactory
//                .selectFrom(member)
//                .fetchOne();

        // 최초 단건 하나 조회(limit(1))
        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();

        // 페이징에서 사용
        // 페이징 정보 포함, total count 쿼리 추가 실행
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal(); // total count 때문에 select count 쿼리문 하나 더 나감
        List<Member> content = results.getResults();

        // count 쿼리로 변경해서 count 수 조회후 반환.
        long total = queryFactory
                .selectFrom(member)
                .fetchCount();
    }


    /**
     * 회원 정렬 순서
     * 1. 회원 나이 내림차순
     * 2. 회원 이름 오름차순
     * 단, 2에서 회원 이름이 없으면 마지막에 출력(nulls last)
     * nullsLast()` , `nullsFirst()` : null 데이터 순서 부여
     */
    @Test
    public void sort() {
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast()) // null이 있으면 null이 최상위 순서
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    // 조회 건수 제한
    @Test
    public void paging1() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);
    }

    // 전체 조회 수가 필요하면?
    @Test
    public void paging2() {
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1) // 시작 지점(row)
                .limit(2) // 페이지 사이즈 (1번째 row부터 2개의 row를 가져옴)
                .fetchResults();

        /**
         * 참고: 실무에서 페이징 쿼리를 작성할 때, 데이터를 조회하는 쿼리는 여러 테이블을 조인해야 하지만,
         * count 쿼리는 조인이 필요 없는 경우도 있다. 그런데 이렇게 자동화된 count 쿼리는 원본 쿼리와 같이 모두 조인을 해버리기 때문에 성능이 안나올 수 있다.
         * count 쿼리에 조인이 필요없는 성능 최적화가 필요하다면, count 전용 쿼리를 별도로 작성해야 한다.
         */
        assertThat(results.getTotal()).isEqualTo(4); // 페이징 되지 않은, offset, limit 를 제거한 쿼리의 전체 레코드 수.
        assertThat(results.getLimit()).isEqualTo(2);
        assertThat(results.getOffset()).isEqualTo(1);
        assertThat(results.getResults().size()).isEqualTo(2);
    }

    @Test
    public void aggregation() {
        List<Tuple> result = queryFactory // Querydsl이 제공하는 Tuple로 반환됨.
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0); // 데이터 타입이 여러 개니까 Tuple로
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);
    }

    /**
     * 팀의 이름별로 각 팀의 평균 연령을 구해라.
     * having 절로 삽입 가능.
     */
    @Test
    public void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    /**
     * 기본 조인
     * 조인의 기본 문법은 첫 번째 파라미터에 조인 대상을 지정하고, 두 번째 파라미터에 별칭(alias)으로 사용할 Q 타입을 지정하면 된다.
     * join(조인 대상, 별칭으로 사용할 Q타입)
     */
    @Test
    public void join() {
        // 팀 A에 소속된 모든 회원
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team) // team의 id와 memeber가 외래키로 받는 team id와 매칭
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1", "member2");
    }

    /**
     * 세타 조인 (연관 관계가 없는 테이블 간의 조인)
     * from 절에 여러 엔티티를 선택해서 세타 조인.
     * 외부 조인 불가능 -> 다음에 설명할 조인 on을 사용하면 외부 조인 가능.
     */
    @Test
    public void theta_join() {
        // 회원의 이름이 팀 이름과 같은 회원 조회
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA", "teamB");
    }

    /**
     * 예) 회원과 팀을 조인 하면서, 팀 이름이 teamA인 팀만 조인, 회원은 모두 조회
     * JPQL: SELECT m, t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
     * SQL: SELECT m.*, t.* FROM Member m LEFT JOIN Team t ON m.TEAM_ID = t.id and t.name = 'teamA'
     * 참고: on 절을 활용해 조인 대상을 필터링 할 때, 외부 조인이 아니라 내부 조인(inner join)을 사용하면,
     * where 절에서 필터링 하는 것과 기능이 동일하다. 따라서 on 절을 활용한 조인 대상 필터링을 사용할 때,
     * 내부 조인 이면 익숙한 where 절로 해결하고, 정말 외부 조인이 필요한 경우에만 이 기능을 사용하자.
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 연관관계가 없는 엔티티 외부 조인 (세타 조인(막조인) left join + on 쓴 버전)
     * 회원의 이름이 팀 이름과 같은 대상 외부 조인
     */
    @Test
    public void join_on_no_relation() {
        // 회원의 이름이 팀 이름과 같은 회원 조회
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name)) // member.team 으로 id로 매칭을 하지만, 이 경우는 팀 이름으로만 매칭
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoinUse() {
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin() // join() 뒤에 .fetchJoin() 붙이기
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue();
    }

    /**
     * 서브 쿼리
     * `com.querydsl.jpa.JPAExpressions' 사용
     * from 절의 서브쿼리 한계**
     * JPA JPQL 서브쿼리의 한계점으로 from 절의 서브쿼리(인라인 뷰)는 지원하지 않는다. 당연히 Querydsl도 지원하지 않는다.
     * 하이버네이트 구현체를 사용하면 select 절의 서브쿼리는 지원한다. Querydsl도 하이버네이트 구현체를 사용 하면 select 절의 서브쿼리를 지원한다.
     * ** from 절의 서브쿼리 해결방안 **
     * 1. 서브쿼리를 join으로 변경한다. (가능한 상황도 있고, 불가능한 상황도 있다.)
     * 2. 애플리케이션에서 쿼리를 2번 분리해서 실행한다.
     * 3. nativeSQL을 사용한다.
     */
    @Test
    public void subQuery() {
        // 나이가 가장 많은 회원 조회
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        select(memberSub.age.max()) // JPAExpressions static import함
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);
    }

    /**
     * 서브 쿼리
     * `com.querydsl.jpa.JPAExpressions' 사용
     */
    @Test
    public void subQueryGoe() {
        // 나이가 평균 이상인 회원
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(30, 40);
    }

    /**
     * 서브 쿼리
     * `com.querydsl.jpa.JPAExpressions' 사용
     */
    @Test
    public void subQueryIn() {
        // 나이가 10살 보다 초과인 사람
        QMember memberSub = new QMember("memberSub");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20, 30, 40);
    }

    @Test
    public void selectSubQuery() {
        QMember memberSub = new QMember("memberSub");

        List<Tuple> result = queryFactory
                .select(member.username,
                        select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * Case 문
     * ** select, 조건절(where), order by에서 사용 가능 **
     * 이런건 DB에서 하지말고 애플리케이션이나, 화면에서 해야함.
     * 단순한 조건
     */
    @Test
    public void basicCase() {
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }

    // 복잡한 조건 : CaseBuilder() 사용
    @Test
    public void complexCase() {
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0 ~ 20살")
                        .when(member.age.between(21, 30)).then("21 ~ 30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }

    /**
     * 순서 정하기
     * 예를 들어서 다음과 같은 임의의 순서로 회원을 출력하고 싶다면?
     * 1. 0 ~ 30살이 아닌 회원을 가장 먼저 출력
     * 2. 0 ~ 20살 회원 출력
     * 3. 21 ~ 30살 회원 출력
     * Querydsl은 자바 코드로 작성하기 때문에 `rankPath` 처럼 복잡한 조건을 변수로 선언해서
     * `select` 절, `orderBy` 절에서 함께 사용할 수 있다.
     */
    @Test
    public void rankPath() {
        NumberExpression<Integer> rankPath = new CaseBuilder()
                .when(member.age.between(0, 20)).then(2)
                .when(member.age.between(21, 30)).then(1)
                .otherwise(3);

        List<Tuple> result = queryFactory
                .select(member.username, member.age, rankPath)
                .from(member)
                .orderBy(rankPath.desc())
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            Integer rank = tuple.get(rankPath);
            System.out.println("username = " + username + " age = " + age + " rank = " + rank);
        }
    }

    /**
     * 상수, 문자 더하기
     * 상수가 필요하면 `Expressions.constant(xxx)` 사용
     */
    @Test
    public void constant() {
        // 참고: 아래와 같이 최적화가 가능하면 SQL에 constant 값을 넘기지 않는다.
        // 상수를 더하는 것 처럼 최적화가 어려우면 SQL에 constant 값을 넘긴다.
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A")) // [member1, A], [member2, B] ...
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    /**
     * 문자 더하기 concat
     * 참고: `member.age.stringValue()` 부분이 중요한데, 문자가 아닌 다른 타입들은 `stringValue()` 로 문자로 변환할 수 있다.
     * 이 방법은 ENUM을 처리할 때도 자주 사용한다.
     */
    @Test
    public void concat() {
        // {username}_{age}
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }

    @Test
    public void simpleProjection() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }

    /**
     * tuple은 리포지토리나 DAO 계층에서만 사용하는 것이 좋은 설계.(컨트롤러, 서비스에서 사용 하면 좋지 않은 설계)
     * 리포지토리 안에서 정리하고 바깥으로 던질 땐 DTO로 던지자.
     */
    @Test
    public void tupleProjection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);

            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }

    /**
     * 프로젝션 - 결과물 DTO 반환 (JPA)
     * 순수 JPA에서 DTO를 조회할 때는 new 명령어를 사용해야함.
     * DTO의 package 이름을 다 적어줘야해서 지저분함.
     * 생성자 방식만 지원함.
     */
    @Test
    public void findDtoByJPQL() {
        List<MemberDto> result = em.createQuery("select new study.querydsl.dto.MemberDto(m.username, m.age) from Member m", MemberDto.class)
                .getResultList();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 프로젝션 - 결과물 DTO 반환 (Querydsl 빈 생성)
     *  -프로퍼티 접근(Setter) -> setter, getter를 통해 값 설정해주고 가져옴.
     *  -필드 직접 접근
     *  -생성자 사용
     */
    @Test
    public void findDtoBySetter() {
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class, // 여기서 bean은 setter를 뜻함.
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * -필드 직접 접근
     * getter, setter 무시하고 필드에 값이 넣어짐.
     */
    @Test
    public void findDtoByField() {
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 프로퍼티나, 필드 접근 생성 방식에서 이름이 다를 때 해결 방안
     * `ExpressionUtils.as(source,alias)` : 필드나, 서브 쿼리에 별칭 적용
     * `username.as("memberName")` : 필드에 별칭 적용
     */
    @Test
    public void findUserDtoByField() {
        QMember memberSub = new QMember("memberSub");

        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"),
                        ExpressionUtils.as(JPAExpressions // 서브쿼리로 age 값을 최대 나이로 모두 출력시키고 싶을 때
                                .select(memberSub.age.max())
                                .from(memberSub), "age")
                ))
                .from(member)
                .fetch();

        for (UserDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * -생성자 사용
     * Dto에 없는 필드인 다른 파라미터가 들어와도 실행하고 나서 런타임 에러가 떠야만 잡힘.(유저가 직접 실행하기 전엔 모름)
     */
    @Test
    public void findDtoByConstructor() {
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username, // MemberDto의 필드와 자료형 타입이 일치해야 딱딱 들어감.
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 생성자 + @QueryProjection : 일반적으로는 이거 쓰고 순수 DTO를 설계하자 싶을 땐, 필드 직접 접근이나, 프로퍼티나 생성자로 그냥 사용.
     * 장점 : 컴파일 시점에 오류를 잡을 수 있음.
     * 단점 : DTO에 QueryDSL 어노테이션을 유지 해야 하는 점과 DTO까지 Q 파일을 생성해야 하는 단점이 있다. (DTO가 querydsl에 의존적임)
     */
    @Test
    public void findDtoByQueryProjection() {
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age)).distinct()
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    /**
     * 동적 쿼리 - BooleanBuilder 사용
     * **동적 쿼리를 해결하는 두가지 방식**
     *  -BooleanBuilder
     *  -Where 다중 파라미터 사용
     */
    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {
//        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond)); 필수 값으로 무조건 있다고 가정했을 때.(앞전에 이미 검증이 끝난)
        BooleanBuilder builder = new BooleanBuilder();

        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    /**
     * 동적 쿼리 - Where 다중 파라미터 사용
     *  -where 조건에 `null` 값은 무시된다.
     *  -메서드를 다른 쿼리에서도 재활용 할 수 있다.
     *  -쿼리 자체의 가독성이 높아진다.
     */
    @Test
    public void dynamicQuery_WhereParam() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond)) // null이 들어오면 그냥 무시됨.
                .where(allEq(usernameCond, ageCond))
                .fetch();
    }

    // 원랜 Predicate -> 아래 메서드에서 한 번에 조립해서 쓰려고 바꾼거
    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    // 원랜 Predicate -> 아래 메서드에서 한 번에 조립해서 쓰려고 바꾼거
    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond == null) {
            return null;
        } else {
            return member.username.eq(usernameCond);
        }
    }

    // 조합한 버전
    // `null` 체크는 따로 주의해서 처리해야함
    // 예시) 광고 상태 isValid, 날짜 IN : isServiceable
    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    @Test
//    @Commit
    public void bulkUpdate() {
        // member1 = 10 -> 비회원
        // member2 = 20 -> 비회원
        // member3 = 30 -> 유지
        // member4 = 40 -> 유지
        // 근데 벌크 연산이라 영속성 컨텍스트는 바뀌지 않고 db만 변경됨.
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();

        // 벌크 연산은 해줘야 데이터가 동일해짐.
        em.flush();
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .fetch();

        // db에 일단 select을 쳐서 바뀐 값을 가져오지만 1차 캐시(영속성 컨텍스트)에 이미 있어서 select 한 건 무시하고 1차 캐시에 있는 값을 가져옴.
        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }

    @Test
    public void bulkAdd() {
        long count = queryFactory
                .update(member)
                .set(member.age, member.age.add(1)) // * : multiply()
                .execute();
    }

    @Test
    public void bulkDelete() {
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }

    /**
     * SQL function 호출하기
     * SQL function은 JPA와 같이 Dialect에 등록된 내용만 호출할 수 있다.
     * member -> M으로 변경하는 replace 함수 사용
     */
    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(Expressions.stringTemplate(
                        "function('replace', {0}, {1}, {2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }

    @Test
    public void sqlFunction2() {
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate(
//                        "function('lower', {0})", member.username)))
                .where(member.username.eq(member.username.lower())) // lower 같은 ansi 표준 함수들은 querydsl이 상당부분 내장하고 있다.
                .fetch();

        for (String string : result) {
            System.out.println("string = " + string);
        }
    }


}

