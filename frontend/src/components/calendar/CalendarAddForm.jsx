import { useCallback, useMemo, useState } from "react"
import { useSWRConfig } from "swr"
import calendarAPI from "../../api/calendar"
// import { addCalendar, useCalendarDispatch } from "../../context"

const CalendarAddForm = () => {
  // const calendarDispatch = useCalendarDispatch()
  const { mutate } = useSWRConfig()
  const [code, setCode] = useState("")
  const [loading, setLoading] = useState(false)

  const handleChange = useCallback((e) => {
    setCode(e.target.value)
  }, [])

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault()
      if (!code) {
        alert("코드를 입력하세요")
        return
      }
      try {
        setLoading(true)
        const calendarRes = await calendarAPI.addShareCalendar({
          calendarCode: code,
        })
        console.log(calendarRes)
        // 캘린더 새로 받아오기 실행
        mutate("/calendar/every/calendars")
      } catch (error) {
        console.log(error.response)
        switch (error.response?.status) {
          case 400: {
            alert("내 캘린더는 추가할 수 없어요")
            break
          }
          case 404: {
            alert("잘못된 캘린더 코드입니다. 다시 확인해주세요.")
            break
          }
          case 409: {
            alert("이미 추가된 캘린더입니다.")
            break
          }
        }
      }
      setLoading(false)
    },
    [code, mutate]
  )

  const canSubmit = useMemo(() => {
    return code && !loading
  }, [code, loading])

  return (
    <form className="grid gap-2" onSubmit={handleSubmit}>
      <div className="form-field">
        <label htmlFor="calendar_code">캘린더 코드</label>
        <div className="input-wrapper">
          <input
            type="text"
            name="code"
            id="calendar_code"
            value={code}
            placeholder="캘린더 코드를 입력하세요"
            onChange={handleChange}
          />
        </div>
      </div>
      <button
        className={`justify-self-end py-2 px-6 text-sm text-white rounded
        ${canSubmit ? "bg-orange-500" : "bg-gray-400"}`}
        disabled={!canSubmit}
      >
        추가
      </button>
    </form>
  )
}

export default CalendarAddForm
