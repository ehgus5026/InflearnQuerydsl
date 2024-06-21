package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
// -프로퍼티 접근(Setter)을 사용하려면 기본 생성자가 있어야 함.
// Querydsl이 MemberDto를 먼저 만들고 다음에 값을 setter로 세팅해야 하는데 빈 깡통 객체를 만들 수 없기 때문.
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class MemberDto {

    private String username;
    private int age;

    // findDtoByConstructor()이 호출될 때, 필드 이름이 아니라 타입을 보고 들어옴.
    @QueryProjection // 생성자에 하고나서 Q파일 생성 시키기.
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }

}
